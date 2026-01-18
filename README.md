# GovSim / Quorum

LLM-driven government simulation plus a Next.js ingestion utility for pulling bills from Congress.gov.

## GovSim (Java)
- Put your bill text at `config/bill.txt` (or set `SIM_BILL_PATH`).
- Update agencies and reps in `config/agencies.json` and `config/representatives.json`.
- Run:

```bash
mvn -q -DskipTests package
java -jar target/govsim-0.1.0-all.jar
```

Output:
- `interaction.log` in the repo root with a timestamp-free log of who spoke and lobby targets.
- Live feed at `http://localhost:8080`.

### Config
Environment overrides (optional):
- `SIM_OLLAMA_URL` (default `http://localhost:11434`)
- `SIM_MODEL` (default `gemma2:2b`)
- `SIM_NUM_PREDICT` (default `600`)
- `SIM_SERVER_PORT` (default `8080`)
- `SIM_MAX_REVISIONS` (default `1`)
- `SIM_FACTS_PATH` (default `config/facts.json`)
- `SIM_BILL_PATH` (default `config/bill.txt`)
- `SIM_AGENCIES_PATH` (default `config/agencies.json`)
- `SIM_REPS_PATH` (default `config/representatives.json`)

### Facts pack
Edit `config/facts.json` with verified statistics you want representatives to cite. The prompts will pull from this file during debate.

## Bill Ingestion (Next.js)
This app pulls bills from the Congress.gov API, extracts text, and caches locally under `src/db`.

```bash
npm install
npm run dev
```

Then open `http://localhost:3000`.

### Env
Create `.env.local` with:

```
CONGRESS_API_KEY=your_key
```

Optional:
```
CONGRESS_API_BASE=https://api.congress.gov/v3
```
