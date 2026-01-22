---
title: Branchline CLI
---

# Branchline CLI

Run Branchline programs on JVM or Node.

## JVM
```bash
./gradlew :cli:runBl --args "path/to/program.bl --input sample.json"
```

## Node
```bash
./gradlew :cli:jsNodeProductionRun --args="path/to/program.bl --input sample.json"
```

## Package the JS CLI
```bash
./gradlew :cli:packageJsCli
```
Output: `cli/build/distributions/branchline-cli-js-<version>.tgz`

## Common flags
- `--input` path to input data
- `--input-format` json | xml
- `--trace` enable tracing output
