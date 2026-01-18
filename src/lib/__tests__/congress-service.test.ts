import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import {
  fetchBillList,
  fetchTextUrl,
  downloadAndStrip,
  ingestOneBillWithText,
  type BillListItem,
} from "../congress-service";

// Mock fetch globally
const mockFetch = vi.fn();
global.fetch = mockFetch;

describe("congress-service", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // Set environment variables for tests
    process.env.CONGRESS_API_KEY = "test-api-key";
    process.env.CONGRESS_API_BASE = "https://api.congress.gov/v3";
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe("fetchBillList", () => {
    it("should fetch bills from Congress 119 endpoint", async () => {
      const mockResponse = {
        bills: [
          {
            congress: 119,
            type: "HR",
            number: "1",
            title: "Test Bill from Congress 119",
            updateDate: "2025-11-15",
            url: "https://api.congress.gov/v3/bill/119/hr/1",
          },
          {
            congress: 119,
            type: "HR",
            number: "2",
            title: "Another Bill from Congress 119",
            updateDate: "2025-11-20",
            url: "https://api.congress.gov/v3/bill/119/hr/2",
          },
        ],
      };

      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockResponse),
      });

      const bills = await fetchBillList(1, 0);

      // Should return only 1 bill (limit=1)
      expect(bills).toHaveLength(1);
      expect(bills[0].title).toBe("Test Bill from Congress 119");
      expect(bills[0].congress).toBe(119);
    });

    it("should include offset and limit parameters in API call", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ bills: [] }),
      });

      await fetchBillList(1, 5);

      expect(mockFetch).toHaveBeenCalledWith(
        expect.stringContaining("/bill/119")
      );
      expect(mockFetch).toHaveBeenCalledWith(
        expect.stringContaining("offset=5")
      );
      expect(mockFetch).toHaveBeenCalledWith(
        expect.stringContaining("limit=1")
      );
    });

    it("should throw error on API failure", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 500,
        statusText: "Internal Server Error",
      });

      await expect(fetchBillList(1)).rejects.toThrow(
        "Failed to fetch bill list: 500 Internal Server Error"
      );
    });
  });

  describe("fetchTextUrl", () => {
    const mockBill: BillListItem = {
      congress: 119,
      type: "HR",
      number: "1",
      title: "Test Bill",
      url: "https://api.congress.gov/v3/bill/119/hr/1",
    };

    it("should return HTML URL when available", async () => {
      const mockResponse = {
        textVersions: [
          {
            date: "2025-11-15",
            formats: [
              { type: "Formatted Text (HTML)", url: "https://congress.gov/bill.html" },
              { type: "Formatted XML", url: "https://congress.gov/bill.xml" },
            ],
          },
        ],
      };

      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockResponse),
      });

      const url = await fetchTextUrl(mockBill);
      expect(url).toBe("https://congress.gov/bill.html");
    });

    it("should fallback to XML URL when HTML not available", async () => {
      const mockResponse = {
        textVersions: [
          {
            date: "2025-11-15",
            formats: [{ type: "Formatted XML", url: "https://congress.gov/bill.xml" }],
          },
        ],
      };

      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockResponse),
      });

      const url = await fetchTextUrl(mockBill);
      expect(url).toBe("https://congress.gov/bill.xml");
    });

    it("should return null when no text versions available", async () => {
      const mockResponse = { textVersions: [] };

      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve(mockResponse),
      });

      const url = await fetchTextUrl(mockBill);
      expect(url).toBeNull();
    });

    it("should return null on API failure", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 404,
      });

      const url = await fetchTextUrl(mockBill);
      expect(url).toBeNull();
    });
  });

  describe("downloadAndStrip", () => {
    it("should extract text from HTML", async () => {
      const html = `
        <!DOCTYPE html>
        <html>
          <head><title>Test</title></head>
          <body>
            <h1>Bill Title</h1>
            <p>This is the bill content.</p>
            <script>console.log('test');</script>
          </body>
        </html>
      `;

      mockFetch.mockResolvedValueOnce({
        ok: true,
        text: () => Promise.resolve(html),
      });

      const text = await downloadAndStrip("https://example.com/bill.html");

      expect(text).toContain("Bill Title");
      expect(text).toContain("This is the bill content.");
      expect(text).not.toContain("console.log");
    });

    it("should normalize whitespace", async () => {
      const html = `
        <body>
          Line   one.


          Line   two.
        </body>
      `;

      mockFetch.mockResolvedValueOnce({
        ok: true,
        text: () => Promise.resolve(html),
      });

      const text = await downloadAndStrip("https://example.com/bill.html");

      expect(text).toBe("Line one. Line two.");
    });

    it("should throw error on fetch failure", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 500,
      });

      await expect(
        downloadAndStrip("https://example.com/bill.html")
      ).rejects.toThrow("Failed to download text");
    });
  });

  describe("ingestOneBillWithText", () => {
    it("should return bill with text when found", async () => {
      // Mock bill list response
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () =>
          Promise.resolve({
            bills: [
              {
                congress: 119,
                type: "HR",
                number: "1",
                title: "Test Bill Title",
                updateDate: "2025-11-15",
                url: "https://api.congress.gov/v3/bill/119/hr/1",
              },
            ],
          }),
      });

      // Mock text URL response
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () =>
          Promise.resolve({
            textVersions: [
              {
                date: "2025-11-15",
                formats: [
                  { type: "Formatted Text (HTML)", url: "https://congress.gov/bill.html" },
                ],
              },
            ],
          }),
      });

      // Mock download response
      mockFetch.mockResolvedValueOnce({
        ok: true,
        text: () => Promise.resolve("<body>Bill text content</body>"),
      });

      const result = await ingestOneBillWithText(0, 0);

      expect(result.bill).not.toBeNull();
      expect(result.bill?.id).toBe(0);
      expect(result.bill?.title).toBe("Test Bill Title");
      expect(result.bill?.rawText).toBe("Bill text content");
      expect(result.finalOffset).toBe(1);
    });

    it("should skip bills without text and try next", async () => {
      // First API call - bill without text
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () =>
          Promise.resolve({
            bills: [
              {
                congress: 119,
                type: "HR",
                number: "1",
                title: "Bill Without Text",
                updateDate: "2025-11-10",
                url: "https://api.congress.gov/v3/bill/119/hr/1",
              },
            ],
          }),
      });

      // No text versions for first bill
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ textVersions: [] }),
      });

      // Second API call - bill with text
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () =>
          Promise.resolve({
            bills: [
              {
                congress: 119,
                type: "HR",
                number: "2",
                title: "Bill With Text",
                updateDate: "2025-11-15",
                url: "https://api.congress.gov/v3/bill/119/hr/2",
              },
            ],
          }),
      });

      // Text URL for second bill
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () =>
          Promise.resolve({
            textVersions: [
              {
                date: "2025-11-15",
                formats: [
                  { type: "Formatted Text (HTML)", url: "https://congress.gov/bill2.html" },
                ],
              },
            ],
          }),
      });

      // Download response for second bill
      mockFetch.mockResolvedValueOnce({
        ok: true,
        text: () => Promise.resolve("<body>Second bill content</body>"),
      });

      const result = await ingestOneBillWithText(5, 0);

      expect(result.bill).not.toBeNull();
      expect(result.bill?.id).toBe(5);
      expect(result.bill?.title).toBe("Bill With Text");
      expect(result.bill?.rawText).toBe("Second bill content");
      expect(result.finalOffset).toBe(2);
    });

    it("should return null when no bills found after max attempts", async () => {
      // First API call - returns a bill
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () =>
          Promise.resolve({
            bills: [
              {
                congress: 119,
                type: "HR",
                number: "1",
                title: "Bill Without Text",
                updateDate: "2025-11-10",
                url: "https://api.congress.gov/v3/bill/119/hr/1",
              },
            ],
          }),
      });

      // No text versions
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ textVersions: [] }),
      });

      // Second API call - empty result
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ bills: [] }),
      });

      const result = await ingestOneBillWithText(0, 0, 2);

      expect(result.bill).toBeNull();
      expect(result.finalOffset).toBe(1); // Stopped at offset 1 when no more bills found
    });
  });
});
