import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import { promises as fs } from "fs";
import path from "path";
import {
  getBills,
  getBillById,
  saveBill,
  saveBills,
  clearBills,
  getBillCount,
  getNextId,
} from "../db-local";
import type { Bill } from "../congress-service";

// Use a test cache file to avoid interfering with actual data
const TEST_CACHE_PATH = path.join(process.cwd(), "src", "db", "bills-cache-test.json");

// Mock the path used in db-local
vi.mock("path", async () => {
  const actual = await vi.importActual<typeof import("path")>("path");
  return {
    ...actual,
    default: {
      ...actual,
      join: (...args: string[]) => {
        const result = actual.join(...args);
        if (result.endsWith("bills-cache.json")) {
          return result.replace("bills-cache.json", "bills-cache-test.json");
        }
        return result;
      },
    },
    join: (...args: string[]) => {
      const result = actual.join(...args);
      if (result.endsWith("bills-cache.json")) {
        return result.replace("bills-cache.json", "bills-cache-test.json");
      }
      return result;
    },
  };
});

const createTestBill = (overrides: Partial<Bill> = {}): Bill => ({
  id: 0,
  title: "Test Bill",
  rawText: "Test bill content",
  ...overrides,
});

describe("db-local", () => {
  beforeEach(async () => {
    // Ensure test cache file is clean before each test
    try {
      await fs.writeFile(TEST_CACHE_PATH, "[]", "utf-8");
    } catch {
      // Directory may not exist yet
      const dir = path.dirname(TEST_CACHE_PATH);
      await fs.mkdir(dir, { recursive: true });
      await fs.writeFile(TEST_CACHE_PATH, "[]", "utf-8");
    }
  });

  afterEach(async () => {
    // Clean up test cache file after each test
    try {
      await fs.unlink(TEST_CACHE_PATH);
    } catch {
      // File may not exist
    }
  });

  describe("getBills", () => {
    it("should return empty array when cache is empty", async () => {
      const bills = await getBills();
      expect(bills).toEqual([]);
    });

    it("should return bills from cache", async () => {
      const testBill = createTestBill();
      await fs.writeFile(TEST_CACHE_PATH, JSON.stringify([testBill]), "utf-8");

      const bills = await getBills();
      expect(bills).toHaveLength(1);
      expect(bills[0].id).toBe(0);
    });

    it("should handle malformed JSON gracefully", async () => {
      await fs.writeFile(TEST_CACHE_PATH, "not valid json", "utf-8");

      const bills = await getBills();
      expect(bills).toEqual([]);
    });
  });

  describe("getBillById", () => {
    it("should return null when bill not found", async () => {
      const bill = await getBillById(999);
      expect(bill).toBeNull();
    });

    it("should return bill when found", async () => {
      const testBill = createTestBill();
      await fs.writeFile(TEST_CACHE_PATH, JSON.stringify([testBill]), "utf-8");

      const bill = await getBillById(0);
      expect(bill).not.toBeNull();
      expect(bill?.title).toBe("Test Bill");
    });
  });

  describe("getNextId", () => {
    it("should return 0 for empty cache", async () => {
      const nextId = await getNextId();
      expect(nextId).toBe(0);
    });

    it("should return next ID based on max existing ID", async () => {
      const bills = [
        createTestBill({ id: 0 }),
        createTestBill({ id: 1 }),
        createTestBill({ id: 5 }),
      ];
      await fs.writeFile(TEST_CACHE_PATH, JSON.stringify(bills), "utf-8");

      const nextId = await getNextId();
      expect(nextId).toBe(6);
    });
  });

  describe("saveBill", () => {
    it("should save a single bill to empty cache", async () => {
      const testBill = createTestBill();

      await saveBill(testBill);

      const bills = await getBills();
      expect(bills).toHaveLength(1);
      expect(bills[0].id).toBe(0);
    });

    it("should update existing bill with same ID", async () => {
      const testBill = createTestBill();
      await saveBill(testBill);

      const updatedBill = createTestBill({ title: "Updated Title" });
      await saveBill(updatedBill);

      const bills = await getBills();
      expect(bills).toHaveLength(1);
      expect(bills[0].title).toBe("Updated Title");
    });
  });

  describe("saveBills", () => {
    it("should save new bills to empty cache", async () => {
      const testBill = createTestBill();

      const result = await saveBills([testBill]);

      expect(result.saved).toBe(1);
      expect(result.updated).toBe(0);

      const bills = await getBills();
      expect(bills).toHaveLength(1);
    });

    it("should update existing bills", async () => {
      const testBill = createTestBill();
      await saveBills([testBill]);

      const updatedBill = createTestBill({ title: "Updated Title" });
      const result = await saveBills([updatedBill]);

      expect(result.saved).toBe(0);
      expect(result.updated).toBe(1);

      const bills = await getBills();
      expect(bills).toHaveLength(1);
      expect(bills[0].title).toBe("Updated Title");
    });

    it("should handle mixed new and existing bills", async () => {
      const bill1 = createTestBill({ id: 0 });
      await saveBills([bill1]);

      const bill2 = createTestBill({ id: 1, title: "Second Bill" });
      const result = await saveBills([bill1, bill2]);

      expect(result.saved).toBe(1);
      expect(result.updated).toBe(1);

      const bills = await getBills();
      expect(bills).toHaveLength(2);
    });
  });

  describe("clearBills", () => {
    it("should clear all bills from cache", async () => {
      const testBill = createTestBill();
      await saveBills([testBill]);

      await clearBills();

      const bills = await getBills();
      expect(bills).toEqual([]);
    });
  });

  describe("getBillCount", () => {
    it("should return 0 for empty cache", async () => {
      const count = await getBillCount();
      expect(count).toBe(0);
    });

    it("should return correct count", async () => {
      const bill1 = createTestBill({ id: 0 });
      const bill2 = createTestBill({ id: 1 });
      await saveBills([bill1, bill2]);

      const count = await getBillCount();
      expect(count).toBe(2);
    });
  });
});
