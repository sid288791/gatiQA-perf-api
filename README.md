# gatiQA-perf-api

Performance testing API for gatiQA. Spring Boot service that runs performance tests via k6 (through the k6 MCP engine) and exposes per-run results and summaries.

## Stack

- Java + Spring Boot
- Gradle build
- k6 MCP performance engine (`engine/k6`)

## Build & Run

```bash
./gradlew bootRun
```

```bash
./gradlew build
```
