import { CONTRACT_CONFIG } from "./contract-config";

export interface ContractBalance {
  wei: string;
  eth: string;
}

export interface ContractEvent {
  type: "BudgetCreated" | "ProofUploaded" | "ItemVerified" | "FundsReleased";
  itemId: string;
  timestamp: number;
  transactionHash: string;
  blockNumber: number;
  amount?: string;
  description?: string;
  url?: string;
}

export interface Transaction {
  hash: string;
  from: string;
  to: string;
  value: string;
  timestamp: number;
  status: "confirmed" | "pending";
  methodName?: string;
}

export interface ContractStats {
  balance: ContractBalance;
  totalItems: number;
  events: ContractEvent[];
  transactions: Transaction[];
}

// Etherscan API key (optional, but recommended for production)
const ETHERSCAN_API_KEY = process.env.NEXT_PUBLIC_ETHERSCAN_API_KEY || "";

async function fetchEtherscan(params: Record<string, string>): Promise<unknown> {
  const queryParams = new URLSearchParams({
    ...params,
    ...(ETHERSCAN_API_KEY && { apikey: ETHERSCAN_API_KEY }),
  });

  const response = await fetch(`${CONTRACT_CONFIG.explorerApiUrl}?${queryParams}`);
  if (!response.ok) {
    throw new Error(`Etherscan API error: ${response.statusText}`);
  }

  const data = await response.json();
  return data;
}

export async function getContractBalance(): Promise<ContractBalance> {
  try {
    const data = await fetchEtherscan({
      module: "account",
      action: "balance",
      address: CONTRACT_CONFIG.address,
      tag: "latest",
    }) as { status: string; result: string };

    if (data.status === "1") {
      const wei = data.result;
      const eth = (BigInt(wei) / BigInt(10 ** 18)).toString() +
        "." +
        (BigInt(wei) % BigInt(10 ** 18)).toString().padStart(18, "0").slice(0, 6);
      return { wei, eth };
    }
    return { wei: "0", eth: "0.000000" };
  } catch (error) {
    console.error("Failed to fetch balance:", error);
    return { wei: "0", eth: "0.000000" };
  }
}

export async function getContractEvents(): Promise<ContractEvent[]> {
  try {
    const data = await fetchEtherscan({
      module: "logs",
      action: "getLogs",
      address: CONTRACT_CONFIG.address,
      fromBlock: "0",
      toBlock: "latest",
    }) as { status: string; result: Array<{
      topics: string[];
      data: string;
      timeStamp: string;
      transactionHash: string;
      blockNumber: string;
    }> };

    if (data.status === "1" && Array.isArray(data.result)) {
      return data.result.map((log) => {
        const topic0 = log.topics[0];
        let type: ContractEvent["type"] = "BudgetCreated";

        // Determine event type by topic signature
        if (topic0?.includes("BudgetCreated") || log.topics.length >= 2) {
          type = "BudgetCreated";
        }
        if (topic0?.includes("ProofUploaded")) {
          type = "ProofUploaded";
        }
        if (topic0?.includes("ItemVerified")) {
          type = "ItemVerified";
        }
        if (topic0?.includes("FundsReleased")) {
          type = "FundsReleased";
        }

        return {
          type,
          itemId: log.topics[1] ? parseInt(log.topics[1], 16).toString() : "0",
          timestamp: parseInt(log.timeStamp, 16) * 1000,
          transactionHash: log.transactionHash,
          blockNumber: parseInt(log.blockNumber, 16),
        };
      }).reverse();
    }
    return [];
  } catch (error) {
    console.error("Failed to fetch events:", error);
    return [];
  }
}

export async function getContractTransactions(): Promise<Transaction[]> {
  try {
    const data = await fetchEtherscan({
      module: "account",
      action: "txlist",
      address: CONTRACT_CONFIG.address,
      startblock: "0",
      endblock: "99999999",
      sort: "desc",
    }) as { status: string; result: Array<{
      hash: string;
      from: string;
      to: string;
      value: string;
      timeStamp: string;
      txreceipt_status: string;
      functionName?: string;
    }> };

    if (data.status === "1" && Array.isArray(data.result)) {
      return data.result.slice(0, 10).map((tx) => ({
        hash: tx.hash,
        from: tx.from,
        to: tx.to,
        value: tx.value,
        timestamp: parseInt(tx.timeStamp) * 1000,
        status: tx.txreceipt_status === "1" ? "confirmed" : "pending",
        methodName: tx.functionName?.split("(")[0] || undefined,
      }));
    }
    return [];
  } catch (error) {
    console.error("Failed to fetch transactions:", error);
    return [];
  }
}

export async function getContractStats(): Promise<ContractStats> {
  const [balance, events, transactions] = await Promise.all([
    getContractBalance(),
    getContractEvents(),
    getContractTransactions(),
  ]);

  // Count items from create events
  const createEvents = events.filter((e) => e.type === "BudgetCreated");
  const totalItems = createEvents.length;

  return {
    balance,
    totalItems,
    events,
    transactions,
  };
}

export function formatAddress(address: string): string {
  return `${address.slice(0, 6)}...${address.slice(-4)}`;
}

export function formatTimestamp(timestamp: number): string {
  const now = Date.now();
  const diff = now - timestamp;

  if (diff < 60000) return "just now";
  if (diff < 3600000) return `${Math.floor(diff / 60000)}m ago`;
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}h ago`;
  return new Date(timestamp).toLocaleDateString();
}

export function weiToEth(wei: string): string {
  const weiNum = BigInt(wei);
  const eth = Number(weiNum) / 10 ** 18;
  return eth.toFixed(6);
}
