# AI usage disclosure

## Tools used

This project was developed with an AI assistant using Copilot SDK in VS Code. No other AI providers or coding agents were used.

## How AI was used

The assistant helped interpret the assignment, implement the Java scenario engine and REST API, build the partitioned Spring Batch re-score, write tests and documentation, and run local validation. The user directed the work incrementally with prompts.

The assistant also used focused prompts during implementation to reproduce test failures, add regression coverage, and verify behavior against H2 and a local PostgreSQL container. The implementation and validation results should be reviewed by the project author.

## Errors and verification

- An early test run exposed a missing datasource configuration in the Spring test context. The failure was reproduced with Maven, the test configuration was corrected, and the full test suite was rerun.
- The first batch integration check established that the job launched, but did not by itself prove parity with real-time alerts. A focused test was added to compare alert details and contributing transaction IDs, cover lookback rows, check all configured partitions, and verify duplicate-free reruns.
- Initial attempts to extract the assignment text with an unavailable PDF utility did not work. The PDF content streams were decoded locally instead, and the Task 3 requirements were checked against the extracted text.
- Batch throughput was not assumed from the implementation. A one-million-row CSV was loaded into a disposable PostgreSQL 17.12 container; the completed job's row count, duration, and planted alerts were checked before the indicative result was added to the README.

## Validation tools

The assistant ran the Maven test suite, Docker Compose configuration checks, and a local PostgreSQL validation run. These checks are useful evidence, not a substitute for reviewing the code, understanding the scenario rules, and repeating performance measurements in a controlled environment.
