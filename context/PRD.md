# CivicForge - Product Requirements Document

## 1) Summary
CivicForge is a web app that ingests government bills and related documents, extracts structured text (via API + Parse AI), routes each bill to one of 10 agencies using an LLM Judge, then runs an agency refinement and floor debate simulation with multiple LLM representatives before concluding with voting and a final bill outcome. It is for hackathon judges and civic-tech users who want to see an end-to-end “AI legislature” pipeline with traceable steps, artifacts, and decisions.

- Assumption: Government bills are accessible via a single configurable REST endpoint returning JSON with bill metadata and a text or document URL.
- Assumption: Parse AI accepts a document URL and returns extracted plain text.
- Assumption: LLM calls use OpenAI Responses API with deterministic settings (temperature 0.2) for reproducibility.
- Assumption: “Popular vote” means authenticated human users voting in-app (not agent vote), with one vote per user per bill version.
- Assumption: The 10 agencies are fixed and seeded at database initialization.
- Assumption: Majority threshold is strictly `> 50%` of valid votes cast; ties fail and return to discussion.

## 2) Tech Stack
- Framework: Next.js 16 App Router with Turbopack
- Database: Supabase PostgreSQL
- Auth: Clerk (email + Google OAuth enabled)
- Styling: Tailwind + shadcn/ui (configured to use `stone` variables)
- Key libs: Zod, React Hook Form
- Deployment: Vercel

## 3) User Roles

| Role | Permissions |
|---|---|
| Anonymous | View bill list, view bill details, view run logs, view outcomes (read-only). |
| Authenticated User | All Anonymous permissions + cast “Popular Vote” (Yes/No) once per bill version; retract vote before poll closes. |
| Admin | Trigger ingestion, re-run pipeline, edit bill metadata (title/summary), open/close popular vote, kill bill, view all raw extracted texts. |

## 4) Data Model

### Entity: Agency
- `id` (uuid, pk)
- `code` (text, unique; values: `AG01`…`AG10`)
- `name` (text)
- `description` (text)
- Relationship: one Agency → many AgentProfiles; one Agency → many BillRuns

### Entity: AgentProfile
- `id` (uuid, pk)
- `agency_id` (uuid, fk → Agency.id, nullable for advocate/judge)
- `role` (enum: `JUDGE`, `REPRESENTATIVE`, `ADVOCATE`)
- `name` (text)
- `model` (text; default `gpt-4.1-mini`)
- `temperature` (numeric; default 0.2)
- `system_prompt` (text)
- Relationships: one AgentProfile → many AgentMessages

### Entity: Bill
- `id` (uuid, pk)
- `external_source` (text; default `government_api`)
- `external_id` (text, unique)
- `title` (text)
- `status` (enum: `INGESTED`, `EXTRACTED`, `ROUTED`, `IN_REFINEMENT`, `ON_FLOOR`, `IN_VOTE`, `PASSED`, `RETURNED_TO_DISCUSSION`, `KILLED`)
- `introduced_at` (timestamptz, nullable)
- `source_url` (text, nullable)
- `raw_api_payload` (jsonb)
- `created_at` (timestamptz, default now)
- Relationship: one Bill → many BillDocuments; one Bill → many BillRuns; one Bill → many BillVersions

### Entity: BillDocument
- `id` (uuid, pk)
- `bill_id` (uuid, fk → Bill.id)
- `doc_type` (enum: `BILL_TEXT`, `AMENDMENT`, `FISCAL_NOTE`, `ATTACHMENT`, `UNKNOWN`)
- `source_url` (text)
- `retrieval_method` (enum: `GOV_API`, `PARSE_AI`)
- `extracted_text` (text, nullable)
- `extraction_status` (enum: `PENDING`, `SUCCEEDED`, `FAILED`)
- `extraction_error` (text, nullable)
- `created_at` (timestamptz, default now)

### Entity: BillRun
- `id` (uuid, pk)
- `bill_id` (uuid, fk → Bill.id)
- `run_number` (int, default 1)
- `status` (enum: `RUNNING`, `SUCCEEDED`, `FAILED`)
- `current_step` (enum: `INGEST`, `EXTRACT`, `JUDGE_ROUTE`, `AGENCY_REFINEMENT`, `PRIMARY_FLOOR`, `AGENT_VOTE`, `POPULAR_VOTE`, `FINALIZE`)
- `routed_agency_id` (uuid, fk → Agency.id, nullable)
- `failure_reason` (text, nullable)
- `started_at` (timestamptz, default now)
- `ended_at` (timestamptz, nullable)

### Entity: RunStep
- `id` (uuid, pk)
- `bill_run_id` (uuid, fk → BillRun.id)
- `step_type` (same enum as BillRun.current_step)
- `status` (enum: `PENDING`, `RUNNING`, `SUCCEEDED`, `FAILED`)
- `input_snapshot` (jsonb)
- `output_snapshot` (jsonb)
- `started_at` (timestamptz, default now)
- `ended_at` (timestamptz, nullable)

### Entity: AgentMessage
- `id` (uuid, pk)
- `bill_run_id` (uuid, fk → BillRun.id)
- `agent_profile_id` (uuid, fk → AgentProfile.id)
- `stage` (enum: `JUDGE`, `REFINE_ROUND_1`, `REFINE_ROUND_2`, `REFINE_ROUND_3`, `FLOOR_ADVOCACY`, `AGENT_VOTE_RATIONALE`)
- `content` (text)
- `created_at` (timestamptz, default now)

### Entity: BillVersion
- `id` (uuid, pk)
- `bill_id` (uuid, fk → Bill.id)
- `bill_run_id` (uuid, fk → BillRun.id)
- `version_number` (int; starts at 1 per bill)
- `title` (text)
- `proposed_text` (text)
- `change_log` (text)
- `created_at` (timestamptz, default now)

### Entity: AgentVoteSummary
- `id` (uuid, pk)
- `bill_run_id` (uuid, fk → BillRun.id)
- `yes_count` (int, default 0)
- `no_count` (int, default 0)
- `abstain_count` (int, default 0)
- `result` (enum: `YES`, `NO`, `TIE`)
- `created_at` (timestamptz, default now)

### Entity: PopularVotePoll
- `id` (uuid, pk)
- `bill_run_id` (uuid, fk → BillRun.id)
- `bill_version_id` (uuid, fk → BillVersion.id)
- `status` (enum: `OPEN`, `CLOSED`)
- `opened_at` (timestamptz, default now)
- `closed_at` (timestamptz, nullable)
- `yes_count` (int, default 0)
- `no_count` (int, default 0)

### Entity: PopularVote
- `id` (uuid, pk)
- `poll_id` (uuid, fk → PopularVotePoll.id)
- `user_id` (text; Clerk user id)
- `vote` (enum: `YES`, `NO`)
- `created_at` (timestamptz, default now)
- Unique constraint: (`poll_id`, `user_id`).

## 5) Core Features

### Feature A: Bill Ingestion (Government API) + Document Extraction (Parse AI)
**User Flow**
1. Admin opens **Admin Console** → clicks **“Ingest Latest Bills”**.
2. System calls government API and upserts `Bill` records by `external_id`.
3. For each bill, system creates `BillDocument` rows.
4. System sets bill `status = INGESTED`.
5. Admin clicks a bill → **“Run Full Pipeline”** to begin extraction.
6. System attempts to populate `extracted_text` via API or Parse AI.

**UI Copy & Colors (Stone Theme)**
- Primary button: **“Run Full Pipeline”** background `#1C1917` (Stone 900), text `#FFFFFF`.
- Secondary button: **“Ingest Latest Bills”** border `#E7E5E4` (Stone 200), text `#1C1917` (Stone 900), background `#FFFFFF`.
- Toast success: **“Ingestion complete.”** background `#1C1917` (Stone 900), text `#FFFFFF`.
- Toast error: **“Ingestion failed. Check API settings.”** background `#B91C1C` (Red 700), text `#FFFFFF`.

**Error & Empty States**
- No bills: Center card text **“No bills ingested yet.”** button **“Ingest Latest Bills”**.
- Parse AI failure: Document row shows **“Extraction failed”** with details and a button **“Retry”** (Text `#57534E`, underline).

**Acceptance Checks**
- Admin can ingest and see at least one `Bill` with `status=INGESTED`.
- `BillDocument.extraction_status` becomes `SUCCEEDED` with text present.

---

### Feature B: LLM Judge Routing to 1 of 10 Agencies
**User Flow**
1. Admin runs pipeline; system enters `JUDGE_ROUTE`.
2. LLM Judge composes prompt and outputs JSON: `{ "agency_code": "AG0X", "rationale": "..." }`.
3. System maps code to Agency, stores `routed_agency_id`.
4. Bill details page displays **“Routed Agency”** and **“Judge Rationale”**.

**UI Copy & Colors (Stone Theme)**
- Section header: **“LLM Judge Decision”** text `#1C1917`.
- Routed badge: background `#E7E5E4` (Stone 200), text `#1C1917` (Stone 900) showing agency name.
- Rationale label: **“Reasoning”** text `#78716C` (Stone 500).

**Error & Empty States**
- Invalid JSON: Banner **“Judge output invalid. Re-run routing.”** background `#F5F5F4`, border `#E7E5E4`.

**Acceptance Checks**
- A BillRun stores `routed_agency_id`.
- Judge rationale is visible on the bill page.

---

### Feature C: Agency Refinement (3 Representatives, 3 Rounds)
**User Flow**
1. System enters `AGENCY_REFINEMENT`.
2. Rounds 1-3: Representatives generate issues, changes, and revised text.
3. Round 3 produces a single “Agency Final Draft” → `BillVersion`.
4. Bill `status` moves to `ON_FLOOR`.

**UI Copy & Colors (Stone Theme)**
- Stepper labels: **“Routing”**, **“Agency Refinement”**, **“Primary Floor”**, **“Agent Vote”**, **“Popular Vote”**. Active step is `#1C1917` (Stone 900), inactive `#A8A29E` (Stone 400).
- Draft card button: **“View Agency Final Draft”** background `#1C1917` (Stone 900), text `#FFFFFF`.
- Representative tags: background `#F5F5F4` (Stone 100), text `#57534E` (Stone 600) (e.g., “AG07 Rep 2”).

**Error & Empty States**
- Failure: **“Agency refinement failed.”** background `#B91C1C` (Red 700), text `#FFFFFF`.

**Acceptance Checks**
- 9 messages stored (3 reps × 3 rounds).
- `BillVersion` created with `proposed_text`.

---

### Feature D: Primary Floor Advocacy + Agent Vote
**User Flow**
1. System enters `PRIMARY_FLOOR`. Advocate LLM generates speech.
2. System enters `AGENT_VOTE`. 30 Representatives cast `YES`, `NO`, or `ABSTAIN`.
3. System aggregates into `AgentVoteSummary`.
4. If `YES` > `NO`: proceed to `POPULAR_VOTE`. Else: `RETURNED_TO_DISCUSSION`.

**UI Copy & Colors (Stone Theme)**
- Advocate header: **“Primary Floor Advocate”** text `#1C1917`.
- Vote totals pills (Semantic colors retained for utility, but muted):
    - YES: background `#15803D` (Green 700), text `#FFFFFF`.
    - NO: background `#B91C1C` (Red 700), text `#FFFFFF`.
    - ABSTAIN: background `#D97706` (Amber 600), text `#FFFFFF`.
- Button if returned: **“Re-run From Agency Refinement”** border `#D97706`, text `#D97706`.

**Acceptance Checks**
- `AgentVoteSummary` exists with counts summing to 30.
- If YES ≤ NO, bill status becomes `RETURNED_TO_DISCUSSION`.

---

### Feature E: Popular Vote + Final Outcome
**User Flow**
1. If agent vote passes, Admin clicks **“Open Popular Vote”**.
2. Authenticated users see banner: **“Popular Vote is Open”**.
3. User votes YES/NO.
4. Admin clicks **“Close Popular Vote”**.
5. Outcome determined (`> 50%` Yes passes).

**UI Copy & Colors (Stone Theme)**
- Open poll banner: background `#1C1917` (Stone 900), text `#FFFFFF`.
- Vote YES button: background `#15803D` (Green 700), text `#FFFFFF`.
- Vote NO button: background `#B91C1C` (Red 700), text `#FFFFFF`.
- Close poll button: background `#FFFFFF`, border `#E7E5E4`, text `#1C1917`.
- Outcome badges:
    - PASSED: background `#15803D`, text `#FFFFFF`.
    - KILLED: background `#B91C1C`, text `#FFFFFF`.

**Acceptance Checks**
- Authenticated user can cast exactly one vote per poll.
- Closing poll sets status to PASSED or KILLED.

## 6) UI/UX

### Color Palette (Stone/Neutral)
*Note: The interface relies on hierarchy through contrast (Black vs White vs Gray) rather than color.*

* **Primary Action (`#1C1917` - Stone 900):** Main buttons (Run Pipeline, Login), Active states, Headings.
* **Secondary/Subtle (`#57534E` - Stone 600):** Secondary text, icons, meta-data.
* **App Background (`#FAFAF9` - Stone 50):** The main canvas color.
* **Surface/Card (`#FFFFFF` - White):** Panels, bills, and modals.
* **Borders (`#E7E5E4` - Stone 200):** Dividers and input borders.
* **Semantic Success (`#15803D` - Green 700):** "Vote Yes", "Passed", "Succeeded".
* **Semantic Danger (`#B91C1C` - Red 700):** "Vote No", "Killed", "Failed", "Error".
* **Semantic Warning (`#D97706` - Amber 600):** "Abstain", "Returned", "Retry".

### Typography
- Font: **Inter** (or similar sans-serif).
- Base: 16px `#1C1917` (Stone 900).
- Muted: 14px `#78716C` (Stone 500).

### Layout Notes
- **Aesthetic:** Minimalist, high-contrast, "editorial". Avoid shadows where possible; prefer clean borders.
- **Components:** Square corners or slightly rounded (`rounded-sm`) to match the structured "Stone" feel.

## 7) Out of Scope (V1)
1. Real-world legislative submissions.
2. Multi-jurisdiction support.
3. Human editing of bill text.
4. Custom user roles.
5. Payment/Subscriptions.
6. Real-time websockets (polling used instead).
7. Multilingual support.
8. Social sharing/comments.

## 8) Success Criteria
- ✓ Admin can ingest bills, run the full pipeline, and reach a terminal outcome.
- ✓ Bill detail page visually adheres to the Stone/Neutral theme (no default Tailwind blue).
- ✓ Empty states appear with explicit Stone-themed CTAs.
- ✓ Error states show exact messages and allow a retry path.
- ✓ App pages load without console errors and state changes are deterministic.