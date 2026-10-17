---
status: In Progress
depends_on: ['planning/branchline-next-stage']
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-10-17
---
# Controlled authoring experiment

Authorized on 2026-10-17: compare Branchline, JSONata and Kotlin using GPT-6.1 Sol, equal budgets and hidden tests. This record precedes scored generation. Product runtime files remain frozen at the locally accepted working tree, based on ce58599.

## Preregistered design

- Thirty separate specifications A01–A30 from the next-stage catalogue, six categories. Three fresh attempts per task/language: 270 independent author sessions, with no cross-task or cross-language answer history.
- Exact model `gpt-6.1-sol`, high reasoning, no substitution. CLI model availability and a non-scored smoke must pass before generation.
- Each attempt gets at most 20 minutes of author/evaluator wall time, an initial answer and at most three repair submissions. Equal maximum sampled-token budget: 12000 per attempt, checked from returned usage; overshoots are reported and disqualified if a hard limit is unavailable. Prompt/pre-fill usage is reported separately because repair history lengths differ.
- Controlled generation with a fixed language reference and public examples supplied in the prompt. No browsing, source exploration or author-side execution; the same evaluator supplies compiler/runtime/public-example diagnostics after a submission. This measures bounded authoring with feedback, not unrestricted IDE productivity.
- Stop repairs when all public examples pass. Hidden tests are executed only after that attempt is frozen, never provided as repair feedback. An overfit public pass is a hidden failure, not a chance for extra repairs.
- At least one public example and three hidden boundary inputs per task. The independently authored oracle and fixture hashes are sealed before scored authors start. Same business specification, explicit absent/null/array-order/rounding rules in each language. No language-specific rejection adapter that performs business logic.
- Author processes must be prevented from reading private tests/results and other answers. Validate the filesystem boundary with a sentinel and audit JSONL tool events. Fresh isolated working directory and empty conversation per attempt; only its own previous submissions appear in repair prompts.
- Use a fixed randomized schedule, three concurrent workers maximum, language order balanced within task/repetition. Record queue time separately, no timing comparisons based on queue delay.
- Record every submission, visible/hidden outcome, engine version, actual model, elapsed time, usage, budget violation and infrastructure error. Infrastructure errors are not language failures; stop/retry under a common predeclared rule (maximum two retries for transport failure, never switch models).
- Primary outcome: hidden-test full-task success within budget. Also first-submission correctness, public repair count and accepted-solution time, with failures retained as right-censored at the budget (report success rate separately). Pair by task and aggregate repetitions before bootstrap; disclose shared-task dependence and small sample limits.
- Continuation threshold from the plan: no correctness regression, and at least 20% lower accepted-solution median time OR 25% fewer repairs, with paired uncertainty excluding no benefit and improvements in more than one category. Require comparison against both baselines for a general claim. A favorable point estimate alone does not establish advantage.
- No production readiness, runtime-speed or full IDE/contract-tooling advantage may be inferred from this experiment. Contract-change tasks test transformation behavior; separate tooling usability remains outside this controlled generation track.

## Delivery

Research artifacts and a reproducible runner live in this directory. Private material stays outside author-visible locations until all sessions finish. The final report must state any preregistration deviation, incomplete cell, limit or failed isolation check.

## Preflight decisions (before scored generation)

- The refreshed local CLI catalog lists the exact requested model and high effort; an isolated non-scored call succeeded. The CLI JSONL reports usage but not a server-resolved model snapshot. Record requested model/configuration and this provenance limitation, rather than inventing returned metadata.
- The installed CLI's experimental rollout-budget configuration differs from current documentation. Do not rely on it as a hard token cap. Enforce 12000 sampled output tokens from completed-turn usage across submissions; reject an overshooting answer, stop further generation and report any overshoot. Input tokens and reasoning-token subcounts are recorded separately; output_tokens already includes reasoning usage.
- Every transport attempt is logged. Unknown usage after a partial answer prevents a free retry. Any unknown transport usage is reported and excludes that cell from efficiency claims. A process interruption never grants a fresh author budget: retain partial artifacts and mark the incomplete cell as infrastructure failure. An exclusive run lock prevents duplicate launch.
- Thirty object-root tasks have 60 public examples and 205 hidden cases. Object roots avoid making a root-output limitation the sole discriminator; empty and singleton nested arrays remain tested. A10 includes all16 valid-state pairs after independent preflight review.
- Author and submitted-code isolation are separately tested. No scored author is launched until normal evaluation, deliberate wrong-output rejection and malicious-file-read rejection pass.
- The fixed language reference cap is 16000 UTF-8 bytes per language. Unequal lengths below the common cap are recorded; padding is not informative documentation. All skill descriptions are removed by a one-token catalogue cap, user config is ignored, tools/web/apps/delegation are disabled, and the same generation instructions are used.
- [Evaluator interface and selftests](evaluator/README.md); [runner](run_experiment.py). Raw logs and authoritative per-cell records are retained for final audit. Queue duration is measured separately and excluded from authoring duration.
