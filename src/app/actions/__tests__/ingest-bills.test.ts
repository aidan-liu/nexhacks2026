import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

// Mock the congress-service module
vi.mock("@/lib/congress-service", () => ({
  ingestOneBillWithText: vi.fn(),
}));

// Mock the db-local module
vi.mock("@/lib/db-local", () => ({
  saveBill: vi.fn(),
  getBills: vi.fn(),
  clearBills: vi.fn(),
  getNextId: vi.fn(),
  getOffset: vi.fn(),
  setOffset: vi.fn(),
  resetOffset: vi.fn(),
}));

import { ingestBillsAction, getBillsAction, clearBillsAction } from "../ingest-bills";
import { ingestOneBillWithText } from "@/lib/congress-service";
import { saveBill, getBills, clearBills, getNextId, getOffset, setOffset, resetOffset } from "@/lib/db-local";
import type { Bill } from "@/lib/congress-service";

const mockIngestOneBillWithText = vi.mocked(ingestOneBillWithText);
const mockSaveBill = vi.mocked(saveBill);
const mockGetBills = vi.mocked(getBills);
const mockClearBills = vi.mocked(clearBills);
const mockGetNextId = vi.mocked(getNextId);
const mockGetOffset = vi.mocked(getOffset);
const mockSetOffset = vi.mocked(setOffset);
const mockResetOffset = vi.mocked(resetOffset);

const createTestBill = (id: number): Bill => ({
  id,
  title: `Test Bill ${id}`,
  rawText: "Test bill content",
});

describe("ingest-bills action", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // Set API key for tests
    process.env.CONGRESS_API_KEY = "test-api-key";
    // Default mock implementations for offset functions
    mockGetOffset.mockResolvedValue(0);
    mockSetOffset.mockResolvedValue(undefined);
    mockResetOffset.mockResolvedValue(undefined);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe("ingestBillsAction", () => {
    it("should return error when API key not configured", async () => {
      delete process.env.CONGRESS_API_KEY;

      const result = await ingestBillsAction();

      expect(result.success).toBe(false);
      expect(result.error).toContain("API key not configured");
    });

    it("should ingest and save a single bill successfully", async () => {
      const testBill = createTestBill(0);

      mockGetNextId.mockResolvedValueOnce(0);
      mockIngestOneBillWithText.mockResolvedValueOnce({ bill: testBill, finalOffset: 1 });
      mockSaveBill.mockResolvedValueOnce(undefined);

      const result = await ingestBillsAction();

      expect(result.success).toBe(true);
      expect(result.bill).toEqual(testBill);

      expect(mockGetNextId).toHaveBeenCalled();
      expect(mockIngestOneBillWithText).toHaveBeenCalledWith(0, expect.any(Number));
      expect(mockSaveBill).toHaveBeenCalledWith(testBill);
    });

    it("should return error when no bill with text found", async () => {
      mockGetNextId.mockResolvedValueOnce(0);
      mockIngestOneBillWithText.mockResolvedValueOnce({ bill: null, finalOffset: 20 });

      const result = await ingestBillsAction();

      expect(result.success).toBe(false);
      expect(result.error).toBe("No bill with extractable text found");
    });

    it("should handle ingestion errors", async () => {
      mockGetNextId.mockResolvedValueOnce(0);
      mockIngestOneBillWithText.mockRejectedValueOnce(new Error("API Error"));

      const result = await ingestBillsAction();

      expect(result.success).toBe(false);
      expect(result.error).toContain("API Error");
    });

    it("should use incrementing IDs", async () => {
      const testBill = createTestBill(5);
      mockGetNextId.mockResolvedValueOnce(5);
      mockIngestOneBillWithText.mockResolvedValueOnce({ bill: testBill, finalOffset: 1 });
      mockSaveBill.mockResolvedValueOnce(undefined);

      const result = await ingestBillsAction();

      expect(result.success).toBe(true);
      expect(result.bill?.id).toBe(5);
      expect(mockIngestOneBillWithText).toHaveBeenCalledWith(5, expect.any(Number));
    });
  });

  describe("getBillsAction", () => {
    it("should return bills from cache", async () => {
      const testBills = [createTestBill(0)];
      mockGetBills.mockResolvedValueOnce(testBills);

      const bills = await getBillsAction();

      expect(bills).toEqual(testBills);
      expect(mockGetBills).toHaveBeenCalled();
    });

    it("should return empty array on error", async () => {
      mockGetBills.mockRejectedValueOnce(new Error("Read error"));

      const bills = await getBillsAction();

      expect(bills).toEqual([]);
    });
  });

  describe("clearBillsAction", () => {
    it("should clear bills successfully", async () => {
      mockClearBills.mockResolvedValueOnce(undefined);

      const result = await clearBillsAction();

      expect(result.success).toBe(true);
      expect(mockClearBills).toHaveBeenCalled();
    });

    it("should handle clear errors", async () => {
      mockClearBills.mockRejectedValueOnce(new Error("Clear error"));

      const result = await clearBillsAction();

      expect(result.success).toBe(false);
      expect(result.error).toBe("Clear error");
    });
  });
});
