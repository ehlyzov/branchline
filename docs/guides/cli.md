---
title: Branchline CLI
---

# Branchline CLI

Run Branchline programs from the command line on JVM or Node.

## JVM CLI
```bash
./gradlew :cli:runBl --args "path/to/program.bl --input sample.json"
```

## Node CLI
```bash
./gradlew :cli:jsNodeProductionRun --args="path/to/program.bl --input sample.json"
```

## Package the JS CLI
```bash
./gradlew :cli:packageJsCli
```
The package is generated at `cli/build/distributions/branchline-cli-js-<version>.tgz`.

## Common flags
- `--input` path to input data
- `--input-format` json | xml
- `--trace` enable tracing output

## Related
- [Install Branchline](install.md)
- [Production Use](production-use.md)
