1. Overview
This feature implements the ingestion of legislative bills from the Congress.gov API. Instead of writing directly to Supabase, we will fetch, sanitize, and store the bill metadata and raw text into a local JSON file (src/db/bills-cache.json). This acts as our "Source of Truth" for the simulation phase.

Goal: Click "Ingest" → API Waterfall (List > Link > Text) → Save to bills-cache.json.

2. Architecture & Data Flow
We use a 3-Step Waterfall to get the actual text, as the API does not provide it in the list view.

Discovery: Fetch list of recent bills (/bill).

Location: For each bill, fetch text metadata (/text).

Download: Fetch the raw HTML/XML and strip tags.

Persist: Write the array of bill objects to ./src/db/bills-cache.json.

3. Implementation Steps
Phase 1: Configuration
Objective: set up API keys and project structure.

Environment Variables:

Add to .env.local:

Bash

CONGRESS_API_KEY=your_key_here
CONGRESS_API_BASE="https://api.congress.gov/v3"
Directory Setup:

Create folder: src/db/

Create empty file: src/db/bills-cache.json (Initialize with []).

Phase 2: The Congress Service (Backend Logic)
Objective: Create the utility functions to handle the API "Waterfall".

File: src/lib/congress-service.ts

Key Functions:

fetchBillList(): Hits /bill endpoint. Returns basic metadata (Title, Number, Congress).

fetchTextUrl(bill): Hits /bill/{congress}/{type}/{number}/text. Parses JSON to find the formattedText URL.

downloadAndStrip(url): Fetches the HTML from the URL found above. Uses a regex or lightweight parser (like cheerio or jsdom) to extract just the body text.

generateId(bill): Create a deterministic ID (e.g., 119-hr-1234) to prevent duplicates.

Phase 3: The File System Manager
Objective: Handle reading/writing to the local JSON file.

File: src/lib/db-local.ts

Logic:

Use Node.js fs/promises.

saveBills(bills: Bill[]): Reads existing file, merges new bills (avoiding duplicates by ID), and overwrites the file.

getBills(): Returns the parsed JSON array.

Phase 4: Server Actions (The Glue)
Objective: Allow the frontend to trigger this Node.js logic.

File: src/app/actions/ingest-bills.ts

Function: ingestBillsAction()

Calls CongressService.fetchBillList().

Loops through results and calls fetchTextUrl + downloadAndStrip (Limit to 3-5 bills initially to avoid timeouts).

Calls LocalDB.saveBills().

Returns { success: true, count: X }.

Phase 5: Admin UI
Objective: The button to trigger the process.

File: src/app/admin/page.tsx

Components:

Button: "Ingest Latest Bills" (Stone-900 background).

Status Indicator: Simple state (loading, success, error) to show progress.

Preview List: Use fs to read the JSON file and display the currently ingested bills below the button.

4. Technical Specifications
Data Structure (JSON Schema)
This is what will live in bills-cache.json:

JSON

[
  {
    "id": "119-hr-45",
    "title": "Tax Repeal Act of 2026",
    "congress": 119,
    "type": "HR",
    "number": "45",
    "introducedDate": "2026-01-15",
    "textSourceUrl": "https://www.congress.gov/...",
    "rawText": "SECTION 1. SHORT TITLE...",
    "status": "INGESTED",
    "ingestedAt": "2026-01-17T12:00:00Z"
  }
]
Dependencies
cheerio: For reliable HTML stripping (Regex is risky for government HTML).

npm install cheerio

5. Development Checklist
[ ] Get API Key from api.congress.gov.

[ ] Create src/lib/congress-service.ts with the 3-step fetch logic.

[ ] Create src/lib/db-local.ts to write to bills-cache.json.

[ ] Build the Admin UI Button component.

[ ] Test: Run ingestion, open bills-cache.json, and verify rawText is readable English (not HTML).