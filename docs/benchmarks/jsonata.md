---
title: JSONata Benchmarks
---

# JSONata Benchmarks

This page tracks Branchline vs JSONata performance on a curated case matrix.

## Latest release results

--8<-- "benchmarks/jsonata/latest.md"

## Release history

--8<-- "benchmarks/jsonata/releases/list.md"

Full archive: [Release benchmark history](releases/).

## Scope

- Default suite: case matrix in `jsonata-benchmarks/src/jmh/resources/jsonata-case-matrix.yaml`.
- Full JSONata test-suite runs are opt-in via `-PjsonataFullSuite=true` or a custom `-PjmhIncludes=...`.

## Run locally

```bash
DASHJOIN_REPO=path/to/dashjoin-jars \
IBM_JSONATA_REPO=path/to/ibm-jars \
./gradlew :jsonata-benchmarks:jmh
```

## Raw artifacts

Latest assets live on the [latest GitHub release](https://github.com/ehlyzov/branchline-public/releases/latest).

Asset names:

- `branchline-jsonata-summary-<tag>.md`
- `branchline-jsonata-summary-<tag>.csv`
- `branchline-jsonata-jmh-<tag>.json`
