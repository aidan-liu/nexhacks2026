import json
import os
import time
from typing import Any, Dict, List, Optional

from dotenv import load_dotenv
from openai import OpenAI
from web3 import Web3


def _require_env(name: str, value: Optional[str]) -> str:
    if not value:
        raise SystemExit(f"Missing required env var: {name}")
    return value


def _looks_like_http_url(value: str) -> bool:
    return value.startswith("http://") or value.startswith("https://")


def _parse_yes_no(full_text: str) -> bool:
    if not full_text:
        return False
    lines = [line.strip() for line in full_text.splitlines() if line.strip()]
    if not lines:
        return False
    first = lines[0].upper()
    return first == "VERDICT: YES"


def verify_receipt_with_ai(client: OpenAI, proof_url: str, amount_wei: int) -> bool:
    amount_eth = Web3.from_wei(amount_wei, "ether")
    amount_eth_str = f"{amount_eth:.18f}".rstrip("0").rstrip(".")

    print(f"AI analyzing proof for escrow amount {amount_wei} wei ({amount_eth_str} ETH)")
    print(f"Proof URL: {proof_url}")

    prompt = f"""
You are the AI Auditor for a Smart Contract Escrow.

Context:
- Money is locked in a contract.
- A contractor submitted a proof document/image for a specific budget line item.

Escrow amount:
- {amount_wei} wei
- {amount_eth_str} ETH

Your job:
1) Determine whether the proof reasonably matches the escrow amount above.
2) Ignore any "Paid" status on the invoice (it may not indicate escrow release).

Output format (exactly):
VERDICT: [YES or NO]
REASON: [brief explanation]
""".strip()

    messages: List[Dict[str, Any]]
    if _looks_like_http_url(proof_url):
        messages = [
            {
                "role": "user",
                "content": [
                    {"type": "text", "text": prompt},
                    {"type": "image_url", "image_url": {"url": proof_url}},
                ],
            }
        ]
    else:
        messages = [{"role": "user", "content": f"{prompt}\n\nProof reference: {proof_url}"}]

    try:
        response = client.chat.completions.create(
            model="openai/gpt-4o",
            messages=messages,
        )
        full_text = (response.choices[0].message.content or "").strip()
    except Exception as e:
        print(f"AI request failed: {e}")
        if _looks_like_http_url(proof_url):
            # Fallback to text-only if the URL isn't a supported image/document.
            try:
                response = client.chat.completions.create(
                    model="openai/gpt-4o",
                    messages=[
                        {
                            "role": "user",
                            "content": f"{prompt}\n\nProof URL (may not be an image): {proof_url}",
                        }
                    ],
                )
                full_text = (response.choices[0].message.content or "").strip()
            except Exception as e2:
                print(f"AI fallback request failed: {e2}")
                return False
        else:
            return False

    print("------------ AI OUTPUT ------------")
    print(full_text)
    print("-----------------------------------")
    return _parse_yes_no(full_text)


def _get_proof_uploaded_events(contract: Any, from_block: int, to_block: int):
    event = contract.events.ProofUploaded()

    # web3.py version differences: (from_block/to_block) vs (fromBlock/toBlock)
    try:
        return event.get_logs(from_block=from_block, to_block=to_block)
    except TypeError:
        return event.get_logs(fromBlock=from_block, toBlock=to_block)
    except Exception:
        # Some providers don't support get_logs on event objects; fallback to filters
        try:
            event_filter = contract.events.ProofUploaded.create_filter(from_block=from_block, to_block=to_block)
        except TypeError:
            event_filter = contract.events.ProofUploaded.create_filter(fromBlock=from_block, toBlock=to_block)
        return event_filter.get_all_entries()


def _build_verify_tx(w3: Web3, account: Any, contract: Any, item_id: int) -> Dict[str, Any]:
    base: Dict[str, Any] = {
        "from": account.address,
        "nonce": w3.eth.get_transaction_count(account.address),
        "chainId": w3.eth.chain_id,
    }

    base_fee = None
    try:
        latest = w3.eth.get_block("latest")
        base_fee = latest.get("baseFeePerGas")
    except Exception:
        base_fee = None

    if base_fee is None:
        base["gasPrice"] = w3.eth.gas_price
    else:
        max_priority_fee = w3.to_wei(2, "gwei")
        max_fee = int(base_fee * 2 + max_priority_fee)
        base["maxPriorityFeePerGas"] = max_priority_fee
        base["maxFeePerGas"] = max_fee

    tx = contract.functions.verifyProof(item_id).build_transaction(base)
    if "gas" not in tx:
        tx["gas"] = int(w3.eth.estimate_gas(tx) * 1.2)
    return tx


def approve_on_chain(w3: Web3, account: Any, contract: Any, private_key: str, item_id: int) -> None:
    print(f"Signing approval tx for item #{item_id}...")
    tx = _build_verify_tx(w3, account, contract, item_id)

    signed_tx = w3.eth.account.sign_transaction(tx, private_key)
    raw_tx = getattr(signed_tx, "rawTransaction", None) or getattr(signed_tx, "raw_transaction")
    tx_hash = w3.eth.send_raw_transaction(raw_tx)

    print(f"Transaction sent: {tx_hash.hex()}")
    print("Waiting for confirmation...")
    w3.eth.wait_for_transaction_receipt(tx_hash)
    print("Verification recorded on-chain.")


def main() -> None:
    load_dotenv()

    openrouter_api_key = _require_env("OPENROUTER_API_KEY", os.getenv("OPENROUTER_API_KEY"))
    contract_address = _require_env("CONTRACT_ADDRESS", os.getenv("CONTRACT_ADDRESS"))
    private_key = _require_env("PRIVATE_KEY", os.getenv("PRIVATE_KEY"))
    rpc_url = _require_env("RPC_URL", os.getenv("RPC_URL"))

    w3 = Web3(Web3.HTTPProvider(rpc_url))
    if not w3.is_connected():
        raise SystemExit(f"Failed to connect to RPC: {rpc_url}")

    account = w3.eth.account.from_key(private_key)
    print("Inspector bot online")
    print(f"Connected RPC: {rpc_url}")
    print(f"Chain ID: {w3.eth.chain_id}")
    print(f"Inspector address: {account.address}")

    with open("artifacts/contracts/BudgetEscrow.sol/BudgetEscrow.json", "r", encoding="utf-8") as f:
        contract_json = json.load(f)
        abi = contract_json["abi"]

    contract = w3.eth.contract(address=Web3.to_checksum_address(contract_address), abi=abi)

    client = OpenAI(
        base_url="https://openrouter.ai/api/v1",
        api_key=openrouter_api_key,
    )

    print("Watching for new ProofUploaded events...")
    last_block = w3.eth.block_number

    while True:
        current_block = w3.eth.block_number
        if current_block > last_block:
            events = _get_proof_uploaded_events(contract, last_block + 1, current_block)
            for event in events:
                item_id = int(event["args"]["id"])
                proof_url = str(event["args"]["url"])

                print(f"\nALERT: Proof uploaded for item #{item_id}")

                item = contract.functions.budgetItems(item_id).call()
                amount_wei = int(item[2])  # struct index: amount

                is_valid = verify_receipt_with_ai(client, proof_url, amount_wei)
                if is_valid:
                    approve_on_chain(w3, account, contract, private_key, item_id)
                else:
                    print("Proof rejected by AI; no transaction sent.")

            last_block = current_block

        time.sleep(2)


if __name__ == "__main__":
    main()

