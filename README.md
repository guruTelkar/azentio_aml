# Project Nightshift

AML transaction monitoring for two scenarios: cash structuring and rapid in-out movement. The real-time API and the nightly re-score share the same framework-independent Java scenario engine.

## Architecture

- `scenario`: immutable transaction and result types, configurable thresholds, and pure Java evaluation.
- `transaction`: transaction persistence and the real-time check flow.
- `alert`: idempotent alert writing, querying, and contributing transaction storage.
- `api`: validated REST requests, masked alert responses, and consistent error bodies.
- `batch`: partitioned, restartable Spring Batch re-score that uses the same scenario engine as the real-time API.
- PostgreSQL schema is managed with Flyway. H2 in PostgreSQL mode is used for the regular test suite.

The real-time lookback uses `(txnTime - window, txnTime]` and an index on `(account_number, txn_time)`. Cash structuring counts qualifying cash credits in the preceding 168 hours. Rapid in-out sums all account credits and debits over the preceding 48 hours for a debit check. All comparisons use `BigDecimal`; event timestamps are instants and alert dates use `Asia/Kolkata`.

## Requirements

- Java 17 or newer and Maven
- Docker Compose for a local PostgreSQL instance; not required for tests

## Run and test

```sh
mvn test
```

Tests use an in-memory H2 database and do not require Docker or PostgreSQL.

For PostgreSQL-backed API requests:

```sh
cp .env.example .env
docker compose up -d postgres
set -a
. ./.env
set +a
mvn spring-boot:run
```

Flyway applies the schema on startup. The API listens on `http://localhost:8080`.

## Partitioned re-score

The `rescoreJob` accepts inclusive `fromDate` and `toDate` parameters in `YYYY-MM-DD` format, interpreted in `Asia/Kolkata`. It divides the 12-digit account-number space into disjoint ranges and reads each partition with an ordered JDBC cursor. Each account is grouped once, including the maximum scenario lookback before `fromDate`, so boundary transactions can contribute to triggers. Batch-created alerts use the same account/scenario/IST-date uniqueness key as real-time alerts.

Generate a reproducible million-row input file (this example covers 2026-10-15 through 2026-11-14):

```sh
mvn -q -DskipTests compile
java -cp target/classes com.azentio.aml.generator.SyntheticTransactionGenerator \
  --rows 1000000 --seed 42 --as-of 2026-11-14T07:00:00Z \
  --output data/transactions.csv --ground-truth data/ground-truth.json
```

Start PostgreSQL and the application once so Flyway creates the tables. Stop the application after startup, then load the CSV with PostgreSQL's client-side `COPY` (the file path is on the host):

```sh
cp .env.example .env
docker compose up -d postgres
set -a
. ./.env
set +a
mvn spring-boot:run
# After Flyway completes, stop the application and load the generated CSV:
PGPASSWORD="$POSTGRES_PASSWORD" psql -h localhost -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  -c "\copy transactions (txn_id, account_number, txn_time, amount, direction, channel, counterparty_account) FROM 'data/transactions.csv' WITH (FORMAT csv, HEADER true)"
```

Run the re-score for that generated data range. `run.id` is an identifying job parameter, so use a new value for each intentional rerun; alert uniqueness prevents duplicate account/scenario/date alerts:

```sh
mvn spring-boot:run \
  -Dspring-boot.run.arguments="--spring.batch.job.enabled=true fromDate=2026-10-15 toDate=2026-11-14 run.id=1"
```

The application logs the job status, transactions read (including lookback rows), newly written alerts, and elapsed time. The integration test checks real-time/batch alert-key parity, lookback handling, partition execution, and duplicate-free reruns.

## API examples

### Cash structuring

Post three cash credits for the same account within 168 hours. The first two do not trigger; the third returns a `CASH_STRUCTURING` result:

```sh
curl -s http://localhost:8080/api/transactions/check \
  -H 'Content-Type: application/json' \
  -d '{"txnId":"cash-1","accountNumber":"123456789012","txnTime":"2026-11-14T06:00:00Z","amount":850000.00,"direction":"CREDIT","channel":"CASH","counterpartyAccount":"987654321098"}'

curl -s http://localhost:8080/api/transactions/check \
  -H 'Content-Type: application/json' \
  -d '{"txnId":"cash-2","accountNumber":"123456789012","txnTime":"2026-11-14T06:30:00Z","amount":850000.00,"direction":"CREDIT","channel":"CASH","counterpartyAccount":"987654321098"}'

curl -s http://localhost:8080/api/transactions/check \
  -H 'Content-Type: application/json' \
  -d '{"txnId":"cash-3","accountNumber":"123456789012","txnTime":"2026-11-14T07:00:00Z","amount":850000.00,"direction":"CREDIT","channel":"CASH","counterpartyAccount":"987654321098"}'
```

Example third response:

```json
{
  "txnId": "cash-3",
  "triggered": [
    {
      "scenarioCode": "CASH_STRUCTURING",
      "detail": { "count": 3, "total": 2550000.00 }
    }
  ],
  "alertIds": ["<alert-uuid>"]
}
```

### Rapid in-out

Post a credit followed by a debit for the same account; an outflow of at least 90% of a qualifying inflow triggers:

```sh
curl -s http://localhost:8080/api/transactions/check \
  -H 'Content-Type: application/json' \
  -d '{"txnId":"rapid-in","accountNumber":"223456789012","txnTime":"2026-11-14T08:00:00Z","amount":1000000.00,"direction":"CREDIT","channel":"NEFT","counterpartyAccount":"887654321098"}'

curl -s http://localhost:8080/api/transactions/check \
  -H 'Content-Type: application/json' \
  -d '{"txnId":"rapid-out","accountNumber":"223456789012","txnTime":"2026-11-14T09:00:00Z","amount":900000.00,"direction":"DEBIT","channel":"IMPS","counterpartyAccount":"887654321098"}'
```

Example second response:

```json
{
  "txnId": "rapid-out",
  "triggered": [
    {
      "scenarioCode": "RAPID_IN_OUT",
      "detail": { "inflow": 1000000.00, "outflow": 900000.00, "ratio": 0.9 }
    }
  ],
  "alertIds": ["<alert-uuid>"]
}
```

List alerts with optional filters and server-side pagination:

```text
GET /api/alerts?scenario=CASH_STRUCTURING&source=REALTIME&from=2026-11-14&to=2026-11-14&page=0&size=20
GET /api/alerts/{id}
```

Account and counterparty account numbers in alert responses are masked to their last four digits.

## Synthetic data generator

The generator streams exactly the requested number of rows to CSV, including known planted accounts and near-miss decoys, and writes a JSON ground-truth file:

```sh
mvn -q -DskipTests compile
java -cp target/classes com.azentio.aml.generator.SyntheticTransactionGenerator \
  --rows 1000000 --seed 42 \
  --output data/transactions.csv \
  --ground-truth data/ground-truth.json
```

The generator includes a three-deposit structuring account, a two-deposit structuring near miss, a 90% rapid in-out account, and an 85% rapid in-out near miss. Remaining rows are deterministic low-value transactions assigned to distinct accounts distributed across the four default batch account partitions, so they do not accidentally trigger either scenario.

## Performance and next steps

One local validation run loaded one million generated rows with PostgreSQL `COPY`, then completed the re-score in 38.99 seconds (about 25.6k rows/second). The job read all 1,000,000 rows and produced the two planted alerts, with no near-miss alerts. This was PostgreSQL 17.12 in a local Docker container, Java 25, and the default four partitions/chunk size 200/fetch size 1000; resources were not pinned, so treat this as indicative rather than a production benchmark.

The next milestone is the React investigator UI and a repeatable, resource-controlled PostgreSQL benchmark.
