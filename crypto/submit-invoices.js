import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { ethers } from "ethers";

function parseArgs() {
  const args = process.argv.slice(2);
  const out = {};
  for (let i = 0; i < args.length; i += 1) {
    const key = args[i];
    if (!key.startsWith("--")) continue;
    out[key.slice(2)] = args[i + 1];
    i += 1;
  }
  return out;
}

const args = parseArgs();
const decisionsPath = args.decisions || "crypto/out/invoice-decisions.json";
const abiPath =
  args.abi ||
  "crypto/ignition/deployments/chain-11155111/artifacts/BudgetModule#BudgetEscrow.json";

const rpcUrl = process.env.ESCROW_RPC_URL;
const privateKey = process.env.ESCROW_PRIVATE_KEY;
const contractAddress = process.env.ESCROW_CONTRACT_ADDRESS;

if (!rpcUrl || !privateKey || !contractAddress) {
  console.error(
    "Missing ESCROW_RPC_URL / ESCROW_PRIVATE_KEY / ESCROW_CONTRACT_ADDRESS."
  );
  process.exit(1);
}

const payload = JSON.parse(readFileSync(resolve(decisionsPath), "utf8"));
const decisions = payload.decisions || [];
const invoices = payload.invoices || [];
const approvals = new Set(
  decisions.filter((d) => d.approved === true).map((d) => d.invoiceId)
);

if (approvals.size === 0) {
  console.log("No approved invoices to submit.");
  process.exit(0);
}

const abi = JSON.parse(readFileSync(resolve(abiPath), "utf8")).abi;
const provider = new ethers.JsonRpcProvider(rpcUrl);
const wallet = new ethers.Wallet(privateKey, provider);
const contract = new ethers.Contract(contractAddress, abi, wallet);

for (const invoice of invoices) {
  if (!approvals.has(invoice.id)) continue;
  const recipient = invoice.recipientAddress || wallet.address;
  const amountWei = invoice.amountWei || "0";
  const desc = `Invoice ${invoice.id}: ${invoice.vendor} - ${invoice.description}`;
  console.log(`Submitting ${invoice.id} (${amountWei} wei) to ${recipient}...`);
  const createTx = await contract.createBudgetItem(desc, recipient, { value: amountWei });
  await createTx.wait();
  const itemId = await contract.itemCount();

  if (recipient.toLowerCase() === wallet.address.toLowerCase()) {
    const proofUrl = invoice.proofUrl || `local://invoices/${invoice.id}`;
    const uploadTx = await contract.uploadProof(itemId, proofUrl);
    await uploadTx.wait();
  }

  const verifyTx = await contract.verifyProof(itemId);
  await verifyTx.wait();
  console.log(`Invoice ${invoice.id} verified on-chain (item ${itemId}).`);
}
