---
title: Install Branchline
---

# Install Branchline

Install and verify Branchline from GitHub Releases.

## Prerequisites
- Java 17+ (for JVM CLI)
- Node 18+ (for Node CLI)

## JVM CLI (recommended for JVM users)
1. Download `branchline-cli-<tag>-all.jar` from GitHub Releases.
2. Run:
```bash
java -jar branchline-cli-<tag>-all.jar path/to/program.bl --input sample.json
```

## Node CLI
1. Download `branchline-cli-js-<tag>.tgz` from GitHub Releases.
2. Extract and run:
```bash
tar -xzf branchline-cli-js-<tag>.tgz
./bin/bl.cjs path/to/program.bl --input sample.json
```

## Common issues
- Use `--input-format xml` when providing XML inputs.

## Related
- [Getting Started](getting-started.md)
- [Branchline CLI](cli.md)
