# Controlled authoring experiment analysis

Failure-penalized sensitivity gate not established; favorable point estimates alone do not establish advantage. The literal preregistered accepted-only time / actual-repair threshold is not established by this gate.

Observed 270/270 cells, 30/30 tasks, and 6/6 categories. The JSON aggregate retains per-task, per-category and per-cell metrics without including source or fixture contents.

## Methodology and limits

Primary correctness is hidden full-task success within the verified common budget, not public-example pass rate. First-submission correctness is reported separately. Infrastructure failures are unobserved language outcomes; unknown transport usage cannot verify the token budget. Known token/time overruns remain disqualified failures in primary and efficiency metrics. An affected unknown-budget task is excluded as a whole from the relevant paired comparison, including all three repetitions in both compared languages. Observed raw correctness is descriptive and may include unverified-budget cells.

Bootstrap seed is 20261017 with 10,000 resamples and two-sided 95% percentile intervals. Each draw resamples task clusters after averaging three repetitions per language. This retains within-task dependence; tasks, not hidden cases or 270 cells, are the units of inference. Complete-design comparisons contain 30 clusters; exclusions reduce that number. The task set is small and curated, and category estimates are exploratory; intervals have no multiplicity adjustment.

Time starts when a worker begins authoring and stops when the public phase freezes. Queue delay and post-freeze hidden evaluation are excluded. Generation/transport time, public compiler time, public evaluator wall time, and hidden evaluation time are reported separately. Every unsuccessful non-infrastructure cell is administratively right-censored at 1200 seconds even if it failed public examples or hit a token/tool limit earlier. The reported restricted time is min(time-to-correct,1200), not a claim that failed cells eventually become correct. The paired time statistic is the median of per-task mean restricted times. Individual-cell restricted means/medians are also shown. Accepted-only times and repairs condition on success and can favor a language that solves only easier tasks; they cannot establish an efficiency advantage.

Failures receive augmented repair burden 4 (beyond the three allowed repairs), so zero repairs followed by early failure is not rewarded. The repair comparison uses mean per-task augmented burden; accepted-only repair counts remain descriptive. A zero baseline denominator makes a reduction and its interval unidentified; undefined bootstrap draws are not silently discarded.

Usage is counted once from transport attempts, falling back to submissions only when transport records are absent. output_tokens includes reasoning tokens; reasoning counts and cached input counts are subsets, not added to their parent totals. Known token totals are lower bounds when usage is missing. Requested model/configuration is gpt-6.1-sol/high; JSONL does not supply a backend-resolved model snapshot.

The failure-penalized operationalized sensitivity gate requires a correctness difference CI with lower endpoint at least zero, plus a point reduction of at least 20% in restricted median time or 25% in augmented repairs with a CI strictly above zero benefit. These estimators differ from the preregistered accepted-only solution-time median and actual public-repair count: failures receive 1200 seconds and repair burden 4, and time is aggregated as a median of per-task means. The literal accepted-only time / actual-repair threshold is not established by this gate. Accepted-only and observed actual-repair statistics remain descriptive. A correctness CI extending below zero cannot establish strict no-regression. The general sensitivity gate requires both baselines, more than one common qualifying category, and complete eligible accounting. There is no inference here about runtime speed, production readiness, unrestricted IDE productivity, or contract-tooling usability.

## Outcomes

| Language | Verified first | Verified final | Budget-verifiable non-infra cells | Raw hidden final | Infra | Unknown usage | Public pass / hidden fail |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| branchline | 90 | 90 | 90 | 90/90 | 0 | 0 | 0 |
| jsonata | 87 | 87 | 90 | 87/90 | 0 | 0 | 3 |
| kotlin | 90 | 90 | 90 | 90/90 | 0 | 0 | 0 |

## Time and repairs

| Language | Restricted time mean / median (s) | Accepted time mean / median (s), n | Accepted repairs mean, n | Observed actual repairs mean, n | Augmented repairs mean |
| --- | ---: | ---: | ---: | ---: | ---: |
| branchline | 13.04 / 10.34 | 13.04 / 10.34, 90 | 0.00, 90 | 0.00, 90 | 0.00 |
| jsonata | 52.50 / 10.30 | 12.93 / 10.13, 87 | 0.00, 87 | 0.00, 90 | 0.13 |
| kotlin | 12.61 / 11.36 | 12.61 / 11.36, 90 | 0.00, 90 | 0.00, 90 | 0.00 |

## Paired Branchline comparisons

Positive correctness differences and positive relative reductions favor Branchline. Intervals are 95% task-cluster intervals. Time and repair comparisons are failure-penalized sensitivity measures, not the literal preregistered accepted-only / actual-repair thresholds.

| Baseline | Correctness clusters / efficiency clusters | Final success difference | Restricted median time reduction | Augmented repair reduction | No-regression established |
| --- | ---: | --- | --- | --- | --- |
| jsonata | 30 / 30 | 3.3% (0.0% to 10.0%) | -4.4% (-26.3% to 25.8%) | 100.0% (CI unavailable) | True |
| kotlin | 30 / 30 | 0.0% (0.0% to 0.0%) | 6.6% (-13.6% to 19.5%) | unavailable (CI unavailable) | True |

## Categories

Counts below are verified final successes / budget-verifiable non-infrastructure records. Category bootstrap details and first-submission counts are in aggregate.json.

| Category | Branchline | JSONata | Kotlin | Operationalized sensitivity gates vs both |
| --- | ---: | ---: | ---: | --- |
| API mapping | 15/15 | 15/15 | 15/15 | False |
| Aggregation | 15/15 | 15/15 | 15/15 | False |
| Contract changes | 15/15 | 15/15 | 15/15 | False |
| Lookup and grouping | 15/15 | 15/15 | 15/15 | False |
| Maintenance and repair | 15/15 | 15/15 | 15/15 | False |
| Validation | 15/15 | 12/15 | 15/15 | False |

Common categories meeting the operationalized sensitivity gate: none established.

## Accounting and failure modes

| Language | Public compile/envelope failures (submissions) | Time limit / token limit / tool cells | Input / sampled output / reasoning subset tokens | Generation / public compile / public evaluator wall / queue (total s) |
| --- | ---: | ---: | ---: | ---: |
| branchline | 0 | 0 / 0 / 0 | 894357 / 39083 / 23889 | 1148.22 / 2.78 / 25.51 / 55912.36 |
| jsonata | 0 | 0 / 0 / 0 | 868617 / 47630 / 33208 | 1164.07 / 1.55 / 22.11 / 55875.06 |
| kotlin | 0 | 0 / 0 / 0 | 900357 / 34855 / 14280 | 971.96 / 140.99 / 162.39 / 55812.96 |

A compiler/envelope failure includes a malformed or missing schema source as well as compile_ok=false diagnostics; it is not automatically a language compiler defect. Token totals above are known totals; a zero reasoning subset can mean the transport did not expose that subcount. Unknown usage cells and affected paired tasks are excluded as described above. Queue totals are shown only for audit and never added to authoring time.

## Deviations and review requirements

- Time estimator difference: the operationalized sensitivity gate uses the median of per-task mean restricted times, with unsuccessful attempts set to 1200 seconds; this is not the literal preregistered accepted-only solution-time median
- Repair estimator difference: the operationalized sensitivity gate uses mean augmented repair burden, with unsuccessful attempts set to 4; this is not the literal preregistered actual public-repair count
- Isolation and seal checks require the runner's independent audit artifacts; this analysis does not establish them from cell outcome records.
- Backend model snapshot is unavailable; only the requested CLI model/configuration is recorded.
- The parent reviewer must inspect preregistration deviations and execution audit before accepting any continuation conclusion.
