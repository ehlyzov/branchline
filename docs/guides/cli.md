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
- `--json-numbers` strict | safe | extended
- `--json-key-mode` string | numeric
- `--output-format` json | json-compact | json-canonical
- `--trace` enable tracing output

Note: The Node CLI uses JavaScript `Number` formatting. For very large or very small floating-point values, the rendered JSON text (including `json-canonical`) can differ slightly from the JVM output due to IEEE-754 rounding and JS stringification. Use `--json-numbers safe` when you need to preserve exact decimal text as strings.
Note: `--json-key-mode numeric` converts object keys that are non-negative integers without leading zeros (except `0`). Top-level input keys remain strings; nested object keys are converted.
