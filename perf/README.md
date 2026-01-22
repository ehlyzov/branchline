# perf workflow notes

This folder holds benchmark outputs, profiling artifacts, and performance notes.

## File naming

- JMH JSON results live under `perf/jmh/`.
- Keep the baseline as `perf/jmh/results-baseline.json` (do not overwrite).
- New runs are copied as:
  - `perf/jmh/results-YYYYMMDD-HHMMSS.json`
  - Optional suffix for focused runs: `perf/jmh/results-YYYYMMDD-HHMMSS-<tag>.json`

Suggested timestamp command:
- `date +%Y%m%d-%H%M%S`

## Usual tasks after measurement

1. Copy the new JMH results from `jsonata-benchmarks/build/results/jmh/results.json` into `perf/jmh/` with the timestamped name.
2. Compare against the baseline and prior runs (focus on interpreter + VM regressions):
   - short script or ad-hoc diff is fine; capture the key deltas.
3. If regressions appear, run focused JMH with more iterations/forks and/or profiling:
   - `-PjmhCaseIds=...` for targeted reruns.
   - `-prof jfr` (store recordings under `perf/jfr-recordings/`, extracts under `perf/jfr/` or `perf/jfr-long/`).
4. Update `perf/report.md` with:
   - date/time (UTC), commands run, and high-level findings.
   - any new anomalies or tasks.
5. If a follow-up is needed, add/adjust items in `perf/tasks.md`.
