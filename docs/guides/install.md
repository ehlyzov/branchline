---
title: Install Branchline
---

# Install Branchline

Install and verify Branchline from GitHub Releases.

## Prerequisites
- Java 21+ (for JVM CLI; release jars target class-file version 65)
- Node 18+ (for Node CLI)

These are launch prerequisites. For a new candidate integration, use the Java
21/25 or Node 22/24 targets and check the exact OS/CPU acceptance in
[Platform Support](platform-support.md). Node 18 is a legacy compatibility check;
the prerequisites do not promise support for every newer runtime or platform.

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
