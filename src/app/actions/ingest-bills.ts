"use server";

import { ingestOneBillWithText } from "@/lib/congress-service";
import { saveBill, getBills, clearBills, getNextId, getOffset, setOffset, resetOffset } from "@/lib/db-local";
import type { Bill } from "@/lib/congress-service";

export interface IngestResult {
  success: boolean;
  bill?: Bill;
  error?: string;
}

/**
 * Server action to ingest a single bill from the Congress.gov API
 * Fetches bills from November 1st, 2025 onwards, extracts text,
 * and saves to local JSON cache with an incrementing ID.
 * Tracks API offset to fetch different bills each time.
 */
export async function ingestBillsAction(): Promise<IngestResult> {
  try {
    // Check if API key is configured
    if (!process.env.CONGRESS_API_KEY) {
      return {
        success: false,
        error: "Congress API key not configured. Please set CONGRESS_API_KEY in .env.local",
      };
    }

    // Get the next available ID and current API offset
    const nextId = await getNextId();
    const currentOffset = await getOffset();

    // Fetch a single bill with text from Congress.gov API, starting from saved offset
    const { bill, finalOffset } = await ingestOneBillWithText(nextId, currentOffset);

    // Always save the new offset so next call continues from where we left off
    await setOffset(finalOffset);

    if (!bill) {
      return {
        success: false,
        error: "No bill with extractable text found",
      };
    }

    // Save to local JSON cache
    await saveBill(bill);

    return {
      success: true,
      bill,
    };
  } catch (error) {
    console.error("Ingestion error:", error);
    return {
      success: false,
      error: error instanceof Error ? error.message : "Unknown error occurred",
    };
  }
}

/**
 * Server action to get all cached bills
 */
export async function getBillsAction(): Promise<Bill[]> {
  try {
    return await getBills();
  } catch (error) {
    console.error("Error getting bills:", error);
    return [];
  }
}

/**
 * Server action to clear all cached bills and reset offset
 * For admin/testing purposes
 */
export async function clearBillsAction(): Promise<{ success: boolean; error?: string }> {
  try {
    await clearBills();
    await resetOffset();
    return { success: true };
  } catch (error) {
    console.error("Error clearing bills:", error);
    return {
      success: false,
      error: error instanceof Error ? error.message : "Unknown error occurred",
    };
  }
}
