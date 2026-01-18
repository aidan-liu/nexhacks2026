import { promises as fs } from "fs";
import path from "path";
import type { Bill } from "./congress-service";

// Path to the bills cache JSON file
const BILLS_CACHE_PATH = path.join(process.cwd(), "src", "db", "bills-cache.json");
// Path to the metadata file (stores offset, etc.)
const METADATA_PATH = path.join(process.cwd(), "src", "db", "metadata.json");

interface Metadata {
  offset: number;
}

const DEFAULT_METADATA: Metadata = { offset: 0 };

/**
 * Ensure the bills cache file exists
 * Creates an empty array if the file doesn't exist
 */
async function ensureCacheFile(): Promise<void> {
  try {
    await fs.access(BILLS_CACHE_PATH);
  } catch {
    // File doesn't exist, create it with empty array
    const dir = path.dirname(BILLS_CACHE_PATH);
    await fs.mkdir(dir, { recursive: true });
    await fs.writeFile(BILLS_CACHE_PATH, "[]", "utf-8");
  }
}

/**
 * Get all bills from the local JSON cache
 * Returns an empty array if the file doesn't exist or is invalid
 */
export async function getBills(): Promise<Bill[]> {
  await ensureCacheFile();

  try {
    const data = await fs.readFile(BILLS_CACHE_PATH, "utf-8");
    const bills = JSON.parse(data);

    if (!Array.isArray(bills)) {
      return [];
    }

    return bills;
  } catch (error) {
    console.error("Error reading bills cache:", error);
    return [];
  }
}

/**
 * Get a single bill by ID
 */
export async function getBillById(id: number): Promise<Bill | null> {
  const bills = await getBills();
  return bills.find((bill) => bill.id === id) || null;
}

/**
 * Get the next available ID for a new bill
 * Returns 0 if there are no existing bills
 */
export async function getNextId(): Promise<number> {
  const bills = await getBills();

  if (bills.length === 0) {
    return 0;
  }

  return Math.max(...bills.map((b) => b.id)) + 1;
}

/**
 * Save a single bill to the local JSON cache
 */
export async function saveBill(bill: Bill): Promise<void> {
  await ensureCacheFile();

  const existingBills = await getBills();

  // Check if bill with this ID already exists
  const existingIndex = existingBills.findIndex((b) => b.id === bill.id);

  if (existingIndex !== -1) {
    // Update existing bill
    existingBills[existingIndex] = bill;
  } else {
    // Add new bill
    existingBills.push(bill);
  }

  await fs.writeFile(
    BILLS_CACHE_PATH,
    JSON.stringify(existingBills, null, 2),
    "utf-8"
  );
}

/**
 * Save multiple bills to the local JSON cache
 * Merges new bills with existing ones, avoiding duplicates by ID
 */
export async function saveBills(newBills: Bill[]): Promise<{ saved: number; updated: number }> {
  await ensureCacheFile();

  // Read existing bills
  const existingBills = await getBills();

  // Create a map of existing bills by ID for efficient lookup
  const billMap = new Map<number, Bill>();
  for (const bill of existingBills) {
    billMap.set(bill.id, bill);
  }

  let updated = 0;
  let saved = 0;

  // Merge new bills - update existing or add new
  for (const newBill of newBills) {
    if (billMap.has(newBill.id)) {
      // Update existing bill
      billMap.set(newBill.id, newBill);
      updated++;
    } else {
      // Add new bill
      billMap.set(newBill.id, newBill);
      saved++;
    }
  }

  // Convert map back to array and save
  const allBills = Array.from(billMap.values());

  await fs.writeFile(
    BILLS_CACHE_PATH,
    JSON.stringify(allBills, null, 2),
    "utf-8"
  );

  return { saved, updated };
}

/**
 * Clear all bills from the cache
 * Useful for testing or resetting the database
 */
export async function clearBills(): Promise<void> {
  await ensureCacheFile();
  await fs.writeFile(BILLS_CACHE_PATH, "[]", "utf-8");
}

/**
 * Get the count of bills in the cache
 */
export async function getBillCount(): Promise<number> {
  const bills = await getBills();
  return bills.length;
}

/**
 * Get the current API offset from metadata
 */
export async function getOffset(): Promise<number> {
  try {
    const data = await fs.readFile(METADATA_PATH, "utf-8");
    const metadata: Metadata = JSON.parse(data);
    return metadata.offset ?? 0;
  } catch {
    // File doesn't exist or is invalid, return default
    return DEFAULT_METADATA.offset;
  }
}

/**
 * Set the API offset in metadata
 */
export async function setOffset(offset: number): Promise<void> {
  const dir = path.dirname(METADATA_PATH);
  await fs.mkdir(dir, { recursive: true });

  const metadata: Metadata = { offset };
  await fs.writeFile(METADATA_PATH, JSON.stringify(metadata, null, 2), "utf-8");
}

/**
 * Increment and return the new offset
 */
export async function incrementOffset(): Promise<number> {
  const currentOffset = await getOffset();
  const newOffset = currentOffset + 1;
  await setOffset(newOffset);
  return newOffset;
}

/**
 * Reset the offset to 0
 */
export async function resetOffset(): Promise<void> {
  await setOffset(0);
}
