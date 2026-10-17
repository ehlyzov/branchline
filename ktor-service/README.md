# Ktor Service Branchline Example

Independent Ktor service demonstrating Kotlin <-> Branchline bridge for a `price-repo`-style ingest flow.

## Contract model (no duplication)
- Source of truth: Branchline contracts from `src/main/resources/branchline/*.bl`.
- Build-time generation:
  - `extractBranchlineContracts` -> `build/generated/branchline/contracts.json`
  - `generateBranchlineDtos` -> generated Kotlin DTOs in `build/generated/source/branchlineContracts/kotlin`
- Runtime strict validation and Kotlin DTO generation both use the same extracted contract artifacts.

## Prerequisites
- JDK 21+
- GitHub Packages credentials for Branchline artifacts
  - `GITHUB_ACTOR`
  - `GITHUB_TOKEN`

Alternative credentials:
- `-Pgpr.user=<actor> -Pgpr.key=<token>`

## Configuration
Pinned Branchline version lives in `gradle.properties`:
- `branchline.version=v0.11.0-RC`

Override example:
```bash
./gradlew -Pbranchline.version=v0.11.1 test
```

Optional local dependency mode (without GitHub credentials):
```bash
./gradlew -Pbranchline.useLocal=true test
```

Optional local jar mode (use freshly built interpreter artifact directly):
```bash
./gradlew -Pbranchline.interpreter.jarPath=/Users/eugene/repo/research/branchline-public/interpreter/build/libs/interpreter-jvm-v0.11.0-SNAPSHOT.jar test
```

## Run
```bash
cd ktor-service
./gradlew run
```

Without GitHub Packages credentials (local branchline-public checkout):
```bash
cd ktor-service
./gradlew -Pbranchline.useLocal=true run
```

Run with explicit local interpreter jar:
```bash
cd ktor-service
./gradlew -Pbranchline.interpreter.jarPath=/Users/eugene/repo/research/branchline-public/interpreter/build/libs/interpreter-jvm-v0.11.0-SNAPSHOT.jar run
```

## Build and test
```bash
cd ktor-service
./gradlew build
```

Fast verification:
```bash
cd ktor-service
./gradlew -Pbranchline.useLocal=true test
```

Reproducible synthetic pilot, stateful replay and the separate three-engine correctness
comparison: [Local pilot guide](docs/LOCAL_PILOT.md).

Manual codegen-only run:
```bash
cd ktor-service
./gradlew extractBranchlineContracts generateBranchlineDtos
```

## API
- `POST /api/v1/price-change`
- `POST /api/v1/price-change/batch`
- `GET /api/v1/prices`
- `GET /api/v1/history`
- `GET /api/v1/outbox/acks`
- `GET /api/v1/outbox/events`

## Design docs
- [INTENT](/Users/eugene/repo/research/branchline-public/ktor-service/docs/INTENT.md)
- [DECISIONS](/Users/eugene/repo/research/branchline-public/ktor-service/docs/DECISIONS.md)
- [CONTRACTS](/Users/eugene/repo/research/branchline-public/ktor-service/docs/CONTRACTS.md)
- [ARCHITECTURE](/Users/eugene/repo/research/branchline-public/ktor-service/docs/ARCHITECTURE.md)
- [Contract Evolution and Versioning Proposal](/Users/eugene/repo/research/branchline-public/ktor-service/docs/CONTRACT_EVOLUTION_AND_VERSIONING.md)
- [Branchline-First Methodology](/Users/eugene/repo/research/branchline-public/ktor-service/docs/METHODOLOGY.md)

## Smoke scenarios
1. Send valid create payload -> accepted ack + updated event.
2. Send newer effectiveTime for same key -> updated status.
3. Send invalid negative price -> rejected ack (`VALIDATION_FAILED`).
4. Send stale payload -> rejected ack (`STALE_RECORD`).
5. Repeat processed transaction ID -> accepted ack only.
