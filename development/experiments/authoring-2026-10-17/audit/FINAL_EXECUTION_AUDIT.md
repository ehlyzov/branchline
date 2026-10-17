# Independent execution audit: scored-002

Verdict: **accepted_execution_integrity**. Audited all **270 cells, 270 source programs and 270 raw author transports** (90 cells per language). All matrix entries, individual results, raw source responses and fixed public-only prompts agree.

No forbidden tool events, source contamination indicators, transport retries, infrastructure errors, repair submissions or budget violations were found. All 270 author thread IDs are distinct; maximum concurrent cells: 3. All 162 sealed file hashes match at the final gate. Seal: `2af8e89ae44e2d1c2cb70116d0d6d73c20e18d4f84d7c25de124187ee187dd6a`.

Sampled output tokens total 121,568; maximum per attempt 1,361 of 12,000. Maximum author/evaluator wall time: 91.669 of 1,200 seconds. Reasoning tokens are already included in output-token accounting; queue time and post-freeze hidden evaluation are excluded from authoring time. Public/hidden fixture files, freeze ordering and raw usage records were checked for every cell. All 540 stored evaluator payloads agree with the results after excluding the runner-added evaluator wall-time field.

The active author sandbox denied a direct sentinel read. Sealed candidate isolation probes and accepted sandbox/runtime artifacts retain their hashes. No feedback was passed to authors.

Limitations: the CLI requested GPT-6.1 Sol/high, but transport JSONL does not attest the actual backend snapshot. Source screening and targeted manual review do not prove arbitrary-code safety or regrade business correctness. SDK skills-budget notices are retained in the JSON report; no tool invocations occurred. Token limits used the preregistered common prompt and post-reply accounting, with no observed overshoot.

Detailed counts, hashes, reproduction command, limits and all 270 source/result/prompt and transport inventories are in `final-execution-audit.json`; lexical inventory is in `final-source-audit.json`.
