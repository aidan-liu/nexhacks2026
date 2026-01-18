# GovSim (LLM-driven)

Minimal simulation scaffold for routing bills to agencies and running a floor debate with persona representatives.

## Quick start
- Put your bill text at `config/bill.txt` (or set `SIM_BILL_PATH`).
- Update agencies and reps in `config/agencies.json` and `config/representatives.json`.
- Set `SIM_OPENROUTER_API_KEY` (and optionally `SIM_MODEL`).
- Run:

```bash
mvn -q -e -DskipTests package
java -jar target/govsim-0.1.0-all.jar
```

Output:
- `interaction.log` in the repo root with a timestamp-free log of who spoke and lobby targets.

## Config
Environment overrides (optional):
- `SIM_OPENROUTER_API_KEY` (or `OPENROUTER_API_KEY`) (required)
- `SIM_OPENROUTER_BASE_URL` (default `https://openrouter.ai/api/v1`)
- `SIM_OPENROUTER_HTTP_REFERER` (optional, recommended by OpenRouter)
- `SIM_OPENROUTER_X_TITLE` (optional, recommended by OpenRouter)
- `SIM_MODEL` (default `x-ai/grok-4.1-fast`, any OpenRouter model id)
- `SIM_NUM_PREDICT` (default `600`)
- `SIM_SERVER_PORT` (default `8080`)
- `SIM_MAX_REVISIONS` (default `1`)
- `SIM_FACTS_PATH` (default `config/facts.json`)
- `SIM_BILL_PATH` (default `config/bill.txt`)
- `SIM_AGENCIES_PATH` (default `config/agencies.json`)
- `SIM_REPS_PATH` (default `config/representatives.json`)

## Facts pack
Edit `config/facts.json` with verified statistics you want representatives to cite. The prompts will pull from this file during debate.
