# GovSim (LLM-driven)

Minimal simulation scaffold for routing bills to agencies and running a floor debate with persona representatives.

## Quick start
- Put your bill text at `config/bill.txt` (or set `SIM_BILL_PATH`).
- Update agencies and reps in `config/agencies.json` and `config/representatives.json`.
- Run:

```bash
mvn -q -e -DskipTests package
java -jar target/govsim-0.1.0-all.jar
```

Output:
- `interaction.log` in the repo root with a timestamp-free log of who spoke and lobby targets.

## Config
Environment overrides (optional):
- `SIM_OLLAMA_URL` (default `http://localhost:11434`)
- `SIM_MODEL` (default `gemma2:2b`)
- `SIM_NUM_PREDICT` (default `200`)
- `SIM_BILL_PATH` (default `config/bill.txt`)
- `SIM_AGENCIES_PATH` (default `config/agencies.json`)
- `SIM_REPS_PATH` (default `config/representatives.json`)
