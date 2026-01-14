---
title: Install Branchline
---

# Install Branchline

Install and verify Branchline for local development.

## Prerequisites
- Java 17+
- Node 18+ (for JS CLI)

## Clone and verify
```bash
git clone https://github.com/ehlyzov/branchline-public.git
cd branchline-public
./gradlew --version
```

## Run on JVM
```bash
./gradlew :cli:runBl --args "path/to/program.bl --input sample.json"
```

## Run on Node
```bash
./gradlew :cli:jsNodeProductionRun --args="path/to/program.bl --input sample.json"
```

## Common issues
- If Gradle is slow, run once to warm caches.
- Use `--input-format xml` when providing XML inputs.

## Related
- [Getting Started](getting-started.md)
- [Branchline CLI](cli.md)
