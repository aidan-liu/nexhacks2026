import * as cheerio from "cheerio";

const CONGRESS_API_BASE =
  process.env.CONGRESS_API_BASE || "https://api.congress.gov/v3";
const CONGRESS_API_KEY = process.env.CONGRESS_API_KEY || "";

// Date filter: June 1st, 2025 to January 17th, 2026 (5 months earlier start date)
const FROM_DATE_TIME = "2025-06-01T00:00:00Z";
const TO_DATE_TIME = "2026-01-17T00:00:00Z";

export interface BillListItem {
  congress: number;
  type: string;
  number: string;
  title: string;
  introducedDate?: string;
  updateDate?: string;
  updateDateIncludingText?: string;
  url: string;
}

// Simplified Bill interface with only essential fields
export interface Bill {
  id: number;       // Sequential integer starting from 0
  title: string;
  rawText: string;  // Required (not nullable)
}

interface CongressApiResponse {
  bills: Array<{
    congress: number;
    type: string;
    number: string;
    title: string;
    introducedDate?: string;
    updateDate?: string;
    updateDateIncludingText?: string;
    url: string;
  }>;
}

interface TextVersionsResponse {
  textVersions: Array<{
    date: string | null;
    formats: Array<{
      type: string;
      url: string;
    }>;
  }>;
}

/**
 * Fetch a list of bills from the Congress.gov API
 * Filters bills to only include those from Congress 118-119 (current and previous Congress)
 * @param limit Number of bills to fetch
 * @param offset Offset for pagination
 * @param fromDateTime (deprecated - no longer used)
 * @param toDateTime (deprecated - no longer used)
 */
export async function fetchBillList(
  limit: number = 1,
  offset: number = 0,
  fromDateTime: string = FROM_DATE_TIME,
  toDateTime: string = TO_DATE_TIME
): Promise<BillListItem[]> {
  // Fetch bills directly from Congress 119 endpoint
  // Congress 119 = 2025-2026
  const url = `${CONGRESS_API_BASE}/bill/119?limit=${limit}&offset=${offset}&format=json&api_key=${CONGRESS_API_KEY}`;

  console.log(`[API] Fetching Congress 119 bills from: ${url}`);

  const response = await fetch(url);

  if (!response.ok) {
    throw new Error(`Failed to fetch bill list: ${response.status} ${response.statusText}`);
  }

  const data: CongressApiResponse = await response.json();
  console.log(`[API] Raw API response: ${data.bills.length} bills returned`);

  if (data.bills && data.bills.length > 0) {
    console.log(`[API] First bill from API:`, JSON.stringify(data.bills[0], null, 2));
  }

  // All bills returned from Congress 119 endpoint are already filtered
  const filteredBills = data.bills;
  console.log(`[API] Got ${filteredBills.length} bills from Congress 119`);

  // Return only the requested limit of filtered bills
  const result = filteredBills.slice(0, limit).map((bill) => ({
    congress: bill.congress,
    type: bill.type,
    number: bill.number,
    title: bill.title,
    introducedDate: bill.introducedDate,
    updateDate: bill.updateDate,
    updateDateIncludingText: bill.updateDateIncludingText,
    url: bill.url,
  }));

  if (result.length > 0) {
    console.log(`[API] Returning bills:`, result.map(b => `Congress ${b.congress}: ${b.title}`));
  }
  console.log(`[API] Returning ${result.length}/${limit} requested bills`);
  return result;
}

/**
 * Fetch the text URL for a specific bill
 * Hits /bill/{congress}/{type}/{number}/text endpoint
 * Returns the URL to the formatted text (HTML/XML)
 */
export async function fetchTextUrl(bill: BillListItem): Promise<string | null> {
  const billId = `${bill.congress}-${bill.type.toLowerCase()}-${bill.number}`;
  const url = `${CONGRESS_API_BASE}/bill/${bill.congress}/${bill.type.toLowerCase()}/${bill.number}/text?format=json&api_key=${CONGRESS_API_KEY}`;

  console.log(`[Text URL] Fetching text versions for ${billId}`);
  console.log(`[Text URL] URL: ${url}`);

  const response = await fetch(url);

  if (!response.ok) {
    console.error(`[Text URL] Failed to fetch text URL for ${billId}: ${response.status} ${response.statusText}`);
    return null;
  }

  const data: TextVersionsResponse = await response.json();
  console.log(`[Text URL] Response:`, JSON.stringify(data, null, 2));

  if (!data.textVersions || data.textVersions.length === 0) {
    console.log(`[Text URL] No text versions found for ${billId}`);
    return null;
  }

  // Get the most recent version (first in array)
  const latestVersion = data.textVersions[0];
  console.log(`[Text URL] Latest version formats:`, latestVersion.formats);

  // Prefer HTML format, fallback to XML
  const htmlFormat = latestVersion.formats.find((f) => f.type === "Formatted Text (HTML)");
  const xmlFormat = latestVersion.formats.find((f) => f.type === "Formatted XML");

  const textUrl = htmlFormat?.url || xmlFormat?.url || null;
  console.log(`[Text URL] Selected URL: ${textUrl || "NONE"}`);
  return textUrl;
}

/**
 * Download HTML/XML content from a URL and strip tags to extract plain text
 * Uses cheerio for reliable HTML parsing
 */
export async function downloadAndStrip(url: string): Promise<string> {
  console.log(`[Download] Downloading from: ${url}`);

  const response = await fetch(url);

  if (!response.ok) {
    throw new Error(`Failed to download text from ${url}: ${response.status}`);
  }

  const html = await response.text();
  console.log(`[Download] Downloaded HTML, length: ${html.length} characters`);
  console.log(`[Download] First 500 chars of HTML: ${html.substring(0, 500)}`);

  // Use cheerio to parse and extract text
  const $ = cheerio.load(html);

  // Remove script and style elements
  $("script, style").remove();

  // Get the body text, or fall back to entire document
  const bodyText = $("body").text() || $.text();

  console.log(`[Download] Extracted text, length: ${bodyText.length} characters`);
  console.log(`[Download] First 300 chars: ${bodyText.substring(0, 300)}`);

  // Clean up whitespace: normalize multiple spaces/newlines
  const cleanedText = bodyText
    .replace(/\s+/g, " ")
    .trim();

  console.log(`[Download] After cleaning, length: ${cleanedText.length} characters`);
  return cleanedText;
}

export interface IngestResult {
  bill: Bill | null;
  finalOffset: number;
}

/**
 * Ingest a single bill with text extraction
 * Keeps searching until a bill with extractable text is found
 * @param nextId The ID to assign to the bill
 * @param startOffset The API offset to start searching from
 * @param maxAttempts Maximum number of bills to try before giving up
 * @returns The bill with text and the final offset, or null if none found
 */
export async function ingestOneBillWithText(
  nextId: number,
  startOffset: number = 0,
  maxAttempts: number = 20
): Promise<IngestResult> {
  let offset = startOffset;
  const maxOffset = startOffset + maxAttempts;

  console.log(`[Ingest] Starting ingestion with ID=${nextId}, offset=${startOffset}, maxAttempts=${maxAttempts}`);

  while (offset < maxOffset) {
    console.log(`[Ingest] Attempting offset ${offset}...`);

    // Fetch 1 bill at a time
    const bills = await fetchBillList(1, offset);
    console.log(`[Ingest] Got ${bills.length} bills from API at offset ${offset}`);

    if (bills.length === 0) {
      console.log(`[Ingest] No more bills available at offset ${offset}`);
      return { bill: null, finalOffset: offset }; // No more bills available
    }

    const billItem = bills[0];
    console.log(`[Ingest] Processing bill: "${billItem.title}" (${billItem.congress}-${billItem.type}-${billItem.number})`);

    // Try to get the text URL
    const textUrl = await fetchTextUrl(billItem);
    console.log(`[Ingest] Text URL: ${textUrl || "NOT FOUND"}`);

    if (textUrl) {
      try {
        const rawText = await downloadAndStrip(textUrl);
        console.log(`[Ingest] Downloaded text, length: ${rawText.length}, trimmed: ${rawText.trim().length}`);

        if (rawText && rawText.trim().length > 0) {
          // Found a bill with text!
          console.log(`[Ingest] SUCCESS! Bill found with ${rawText.trim().length} characters of text`);
          return {
            bill: {
              id: nextId,
              title: billItem.title,
              rawText,
            },
            finalOffset: offset + 1, // Next call should start from the next bill
          };
        } else {
          console.log(`[Ingest] Text is empty, trying next bill`);
        }
      } catch (error) {
        console.error(`[Ingest] Error downloading text for bill:`, error);
      }
    } else {
      console.log(`[Ingest] No text URL found for this bill, trying next`);
    }

    // No text found, try next bill
    offset++;
  }

  console.log(`[Ingest] FAILED: No bill with text found after ${maxAttempts} attempts`);
  return { bill: null, finalOffset: offset }; // No bill with text found after max attempts
}

/**
 * Legacy function for backwards compatibility
 * @deprecated Use ingestOneBillWithText instead
 */
export async function ingestBills(limit: number = 1): Promise<Bill[]> {
  const { bill } = await ingestOneBillWithText(0);
  return bill ? [bill] : [];
}
