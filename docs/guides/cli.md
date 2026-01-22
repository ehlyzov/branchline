---
title: Branchline CLI
---

# Branchline CLI

Run Branchline programs on JVM or Node.

## JVM
Download `branchline-cli-<tag>-all.jar` from GitHub Releases and run:
```bash
java -jar branchline-cli-<tag>-all.jar path/to/program.bl --input sample.json
```

## Node
Download `branchline-cli-js-<tag>.tgz` from GitHub Releases, extract, and run:
```bash
tar -xzf branchline-cli-js-<tag>.tgz
./bin/bl.cjs path/to/program.bl --input sample.json
```

## Common flags
- `--input` path to input data
- `--input-format` json | xml
- `--trace` enable tracing output
