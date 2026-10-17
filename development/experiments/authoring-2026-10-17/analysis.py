#!/usr/bin/env python3
"""Analyze frozen authoring cells without opening fixtures, source, or raw author logs.

Usage: python3 analysis.py --input /tmp/branchline-authoring-private/all-results.json
                          --output-dir /path/to/report
Writes aggregate.json and report.md. The report doubles as the methodology template
when no complete pairs exist. Fixed preregistration: 30 tasks, 3 repetitions, 3
languages, 1200 seconds, 12000 sampled tokens, seed 20261017, 10000 bootstraps.
Only the Python standard library is required. No inference uses submission cases
as independent observations; hidden case contents are never included in output.
"""
import argparse
from collections import Counter, defaultdict
import hashlib
import json
import math
from pathlib import Path
import random
import statistics

LANGUAGES = ("branchline", "jsonata", "kotlin")
REPEATS = (1, 2, 3)
TASKS = 30
SECONDS = 1200.0
TOKENS = 12000
SEED = 20261017
BOOTSTRAPS = 10000


def number(value):
    return isinstance(value, (int, float)) and not isinstance(value, bool) and math.isfinite(value)


def passed(evaluation):
    return bool(isinstance(evaluation, dict) and evaluation.get("compile_ok")
                and evaluation.get("cases")
                and all(case.get("ok") is True for case in evaluation["cases"]))


def describe(values):
    values = [float(x) for x in values if number(x)]
    return {"n": len(values), "mean": statistics.mean(values) if values else None,
            "median": statistics.median(values) if values else None,
            "total": sum(values) if values else None}


def usage_value(usage, name):
    if not isinstance(usage, dict):
        return None
    if name == "reasoning_output_tokens":
        value = usage.get(name, usage.get("reasoning_tokens"))
        if value is None:
            value = (usage.get("output_tokens_details") or {}).get("reasoning_tokens")
    elif name == "cached_input_tokens":
        value = usage.get(name)
        if value is None:
            value = (usage.get("input_tokens_details") or {}).get("cached_tokens")
    else:
        value = usage.get(name)
    return value if number(value) and value >= 0 else None


def normalize(raw):
    """Extract only metrics. Transport and submission usage must not be added together."""
    submissions = raw.get("submissions") or []
    indexed = {s.get("index", i): s for i, s in enumerate(submissions)}
    first = indexed.get(0, {})
    final = indexed.get(raw.get("selected_submission"), {})
    transports = raw.get("transport_attempts") or []
    # Legacy/synthetic records can supply submission usage without transports.
    accounting = transports if transports else submissions
    usages = [entry.get("usage") for entry in accounting]
    unknown_usage = bool(raw.get("unknown_transport_usage") or raw.get("unknown_sampled_usage")
                         or not usages or any(usage_value(u, "output_tokens") is None for u in usages))
    totals = {}
    for name in ("input_tokens", "cached_input_tokens", "output_tokens", "reasoning_output_tokens"):
        known = [usage_value(u, name) for u in usages]
        totals[name] = {"known_total": sum(v for v in known if v is not None),
                        "known_entries": sum(v is not None for v in known), "entries": len(known)}
    known_output = totals["output_tokens"]["known_total"]
    sampled = raw.get("sampled_tokens")
    output_lower_bound = max(known_output, sampled if number(sampled) else 0)
    elapsed = raw.get("authoring_seconds")
    infra = (raw.get("status") in ("infrastructure_error", "running")
             or any((s.get(part) or {}).get("infrastructure_error")
                    for s in submissions for part in ("public", "hidden")))
    tool_entries = accounting
    tool_count = sum(len(t.get("tool_events") or []) for t in tool_entries)
    token_violation = output_lower_bound > TOKENS
    time_violation = number(elapsed) and elapsed > SECONDS
    budget_verified = (not unknown_usage and not token_violation and not time_violation
                       and raw.get("budget_verified", True) is not False)
    # Never reclassify infrastructure as a language failure. Unknown accounting is
    # not proof that a correct artifact was produced within the common budget.
    known_disqualification = token_violation or time_violation or raw.get("status") in ("budget_exhausted", "time_budget", "tool_protocol_violation")
    primary_eligible = not infra and not unknown_usage and (budget_verified or known_disqualification)
    raw_final_success = passed(final.get("public")) and passed(final.get("hidden"))
    raw_initial_success = passed(first.get("public")) and passed(first.get("hidden"))
    disqualified = (raw.get("status") in ("budget_exhausted", "time_budget", "tool_protocol_violation")
                    or tool_count > 0 or token_violation or time_violation)
    final_success = bool(raw_final_success and raw.get("success") and not disqualified)
    initial_success = bool(raw_initial_success and not disqualified)
    repairs = raw.get("repairs")
    if not number(repairs):
        repairs = max(0, len(submissions) - 1)
    hidden_present = bool(final.get("hidden"))
    public_pass = passed(final.get("public"))
    compile_public = [s["public"] for s in submissions if isinstance(s.get("public"), dict)]
    hidden_eval = [s["hidden"] for s in submissions if isinstance(s.get("hidden"), dict)]
    author_parts = [t.get("elapsed_seconds") for t in transports] if transports else [s.get("author_seconds") for s in submissions]
    issues = []
    if raw.get("success") and not raw_final_success:
        issues.append("reported_success_without_confirming_public_and_hidden_evaluation")
    if raw.get("success") and disqualified:
        issues.append("reported_success_on_disqualified_cell")
    if raw.get("model") not in (None, "gpt-6.1-sol"):
        issues.append("requested_model_mismatch")
    if raw.get("reasoning") not in (None, "high"):
        issues.append("reasoning_configuration_mismatch")
    if final_success and not number(elapsed):
        issues.append("accepted_time_missing")
    return {"task_id": raw["task_id"], "language": raw["language"], "repeat": raw["repeat"],
            "category": raw.get("category", "unknown"), "status": raw.get("status", "unknown"),
            "infrastructure_error": bool(infra), "unknown_usage": unknown_usage,
            "budget_verified": budget_verified, "primary_eligible": primary_eligible,
            "efficiency_eligible": primary_eligible and (not final_success or number(elapsed)),
            "observed_initial_correct": raw_initial_success, "observed_final_correct": raw_final_success,
            "initial_success": initial_success, "final_success": final_success,
            "initial_evaluated": bool(first.get("public") and first.get("hidden")),
            "final_hidden_evaluated": hidden_present,
            "public_pass_hidden_fail": public_pass and hidden_present and not passed(final.get("hidden")),
            "public_pass_hidden_unobserved": public_pass and not hidden_present,
            "public_pass": public_pass, "submissions": len(submissions), "repairs": repairs,
            "augmented_repairs": repairs if final_success else 4,
            "restricted_seconds": min(SECONDS, float(elapsed)) if final_success and number(elapsed) else SECONDS,
            "authoring_seconds": elapsed if number(elapsed) else None,
            "author_generation_seconds": sum(x for x in author_parts if number(x)),
            "queue_seconds": raw.get("queue_seconds") if number(raw.get("queue_seconds")) else None,
            "public_compile_seconds": sum(e.get("compile_seconds", 0) or 0 for e in compile_public),
            "public_execute_seconds": sum(e.get("execute_seconds", 0) or 0 for e in compile_public),
            "public_evaluator_wall_seconds": sum(e.get("evaluator_wall_seconds", 0) or 0 for e in compile_public),
            "hidden_compile_seconds": sum(e.get("compile_seconds", 0) or 0 for e in hidden_eval),
            "hidden_execute_seconds": sum(e.get("execute_seconds", 0) or 0 for e in hidden_eval),
            "compile_fail_submissions": sum(e.get("compile_ok") is False for e in compile_public),
            "compile_fail_initial": first.get("public", {}).get("compile_ok") is False,
            "compile_fail_final": final.get("public", {}).get("compile_ok") is False,
            "transport_attempts": len(transports), "transport_retries": sum(t.get("retry", 0) > 0 for t in transports),
            "incomplete_transports": sum(not t.get("completed", True) for t in transports),
            "tool_events": tool_count, "tool_violation": tool_count > 0 or raw.get("status") == "tool_protocol_violation",
            "time_limit_reached": bool(raw.get("status") == "time_budget" or time_violation
                                       or any(t.get("timeout") for t in transports)),
            "time_budget_violation": bool(time_violation), "token_budget_violation": token_violation,
            "token_limit_reached": output_lower_bound >= TOKENS,
            "sampled_output_lower_bound": output_lower_bound, "usage": totals, "issues": issues}


def summarize(cells):
    eligible = [c for c in cells if c["primary_eligible"]]
    efficient = [c for c in cells if c["efficiency_eligible"]]
    accepted = [c for c in efficient if c["final_success"]]
    out = {"records": len(cells), "primary_eligible_records": len(eligible),
           "observed_initial_correct": sum(c["observed_initial_correct"] for c in cells),
           "observed_final_correct": sum(c["observed_final_correct"] for c in cells),
           "verified_initial_success": sum(c["initial_success"] for c in eligible),
           "verified_final_success": sum(c["final_success"] for c in eligible),
           "verified_final_failure": sum(not c["final_success"] for c in eligible),
           "status_counts": dict(sorted(Counter(c["status"] for c in cells).items()))}
    for key in ("infrastructure_error", "unknown_usage", "budget_verified", "initial_evaluated",
                "final_hidden_evaluated", "public_pass", "public_pass_hidden_fail", "public_pass_hidden_unobserved",
                "compile_fail_initial", "compile_fail_final", "compile_fail_submissions", "transport_attempts",
                "transport_retries", "incomplete_transports", "tool_events", "tool_violation", "time_limit_reached",
                "time_budget_violation", "token_limit_reached", "token_budget_violation"):
        out[key] = sum(c[key] for c in cells)
    for key in ("authoring_seconds", "author_generation_seconds", "queue_seconds", "public_compile_seconds",
                "public_execute_seconds", "public_evaluator_wall_seconds", "hidden_compile_seconds", "hidden_execute_seconds"):
        out[key] = describe(c[key] for c in cells)
    out["restricted_time_to_correct_seconds"] = describe(c["restricted_seconds"] for c in efficient)
    out["conditional_accepted_time_seconds"] = describe(c["authoring_seconds"] for c in accepted)
    out["conditional_accepted_repairs"] = describe(c["repairs"] for c in accepted)
    out["observed_repairs_all_records"] = describe(c["repairs"] for c in cells)
    out["augmented_repair_burden"] = describe(c["augmented_repairs"] for c in efficient)
    out["usage"] = {name: {field: sum(c["usage"][name][field] for c in cells)
                           for field in ("known_total", "known_entries", "entries")}
                    for name in ("input_tokens", "cached_input_tokens", "output_tokens", "reasoning_output_tokens")}
    return out


def quantile(values, p):
    ordered = sorted(values)
    position = (len(ordered) - 1) * p
    lo, hi = math.floor(position), math.ceil(position)
    return ordered[lo] + (ordered[hi] - ordered[lo]) * (position - lo)


def bootstrap(pairs, metric, *, samples=BOOTSTRAPS, seed=SEED):
    """Percentile cluster bootstrap. Each pair already averages three repeats."""
    def estimate(rows):
        a = [r[0] for r in rows]
        b = [r[1] for r in rows]
        if metric == "success_difference":
            return statistics.mean(a) - statistics.mean(b)
        if metric == "time_reduction":
            denom = statistics.median(b)
            return 1 - statistics.median(a) / denom if denom else None
        denom = statistics.mean(b)
        return 1 - statistics.mean(a) / denom if denom else None
    if not pairs:
        return {"estimate": None, "ci95": None, "task_clusters": 0, "undefined_resamples": 0}
    point = estimate(pairs)
    rng = random.Random(seed)
    draws = [estimate([pairs[rng.randrange(len(pairs))] for _ in pairs]) for _ in range(samples)]
    undefined = sum(v is None for v in draws)
    # Dropping undefined zero-denominator draws would condition the CI silently.
    interval = None if undefined else [quantile(draws, .025), quantile(draws, .975)]
    return {"estimate": point, "ci95": interval, "task_clusters": len(pairs),
            "undefined_resamples": undefined, "bootstrap_samples": samples, "seed": seed}


def compare(cells, baseline, *, samples=BOOTSTRAPS):
    grouped = defaultdict(list)
    for c in cells:
        if c["language"] in ("branchline", baseline):
            grouped[c["task_id"], c["language"]].append(c)
    tasks = sorted({c["task_id"] for c in cells})
    primary = []
    efficiency = []
    excluded = {}
    for task in tasks:
        groups = [grouped[task, lang] for lang in ("branchline", baseline)]
        reasons = []
        if any(sorted(c["repeat"] for c in g) != list(REPEATS) for g in groups):
            reasons.append("missing_repetition")
        if any(c["infrastructure_error"] for g in groups for c in g):
            reasons.append("infrastructure_error")
        if any(not c["primary_eligible"] and not c["infrastructure_error"] for g in groups for c in g):
            reasons.append("unknown_or_unverified_budget")
        if reasons:
            excluded[task] = sorted(set(reasons))
            continue
        primary.append((task, *[statistics.mean(c["final_success"] for c in g) for g in groups]))
        if all(c["efficiency_eligible"] for g in groups for c in g):
            efficiency.append((task, groups))
        else:
            excluded[task] = ["accepted_time_missing_efficiency_only"]
    correctness = bootstrap([(x[1], x[2]) for x in primary], "success_difference", samples=samples)
    initial = []
    for task, *_ in primary:
        initial.append(tuple(statistics.mean(c["initial_success"] for c in grouped[task, lang])
                             for lang in ("branchline", baseline)))
    time_pairs = [tuple(statistics.mean(c["restricted_seconds"] for c in g) for g in groups)
                  for _, groups in efficiency]
    repair_pairs = [tuple(statistics.mean(c["augmented_repairs"] for c in g) for g in groups)
                    for _, groups in efficiency]
    time_result = bootstrap(time_pairs, "time_reduction", samples=samples)
    repair_result = bootstrap(repair_pairs, "repair_reduction", samples=samples)
    nonregression = correctness["ci95"] is not None and correctness["ci95"][0] >= 0
    def benefit(value, threshold):
        return bool(value["estimate"] is not None and value["estimate"] >= threshold
                    and value["ci95"] is not None and value["ci95"][0] > 0)
    time_gate, repair_gate = benefit(time_result, .20), benefit(repair_result, .25)
    return {"baseline": baseline, "primary_task_ids": [r[0] for r in primary],
            "efficiency_task_ids": [r[0] for r in efficiency], "excluded_tasks": excluded,
            "final_success_difference_branchline_minus_baseline": correctness,
            "initial_success_difference_branchline_minus_baseline": bootstrap(initial, "success_difference", samples=samples),
            "restricted_median_time_relative_reduction": time_result,
            "augmented_mean_repair_relative_reduction": repair_result,
            "correctness_nonregression_established": nonregression,
            "time_20_percent_point_and_ci_excludes_no_benefit": time_gate,
            "repair_25_percent_point_and_ci_excludes_no_benefit": repair_gate,
            "correctness_and_efficiency_gate": bool(nonregression and (time_gate or repair_gate))}


def analyze(records, *, samples=BOOTSTRAPS):
    if not isinstance(records, list):
        raise ValueError("Expected all-results.json to contain a JSON array of cells")
    identities = set()
    categories = {}
    cells = []
    for r in records:
        if r.get("language") not in LANGUAGES or r.get("repeat") not in REPEATS or not r.get("task_id"):
            raise ValueError("Unexpected language, repeat, or missing task_id")
        identity = r["task_id"], r["language"], r["repeat"]
        if identity in identities:
            raise ValueError(f"Duplicate cell: {identity}")
        identities.add(identity)
        category = r.get("category", "unknown")
        if r["task_id"] in categories and categories[r["task_id"]] != category:
            raise ValueError(f"Category disagrees across repetitions: {r['task_id']}")
        categories[r["task_id"]] = category
        cells.append(normalize(r))
    tasks = sorted(categories)
    missing = [{"task_id": t, "language": l, "repeat": r} for t in tasks for l in LANGUAGES for r in REPEATS
               if (t, l, r) not in identities]
    complete = len(tasks) == TASKS and len(cells) == TASKS * 9 and not missing
    by_language = {l: summarize([c for c in cells if c["language"] == l]) for l in LANGUAGES}
    by_task = {t: {l: summarize([c for c in cells if c["task_id"] == t and c["language"] == l])
                   for l in LANGUAGES} for t in tasks}
    by_category = {cat: {l: summarize([c for c in cells if c["category"] == cat and c["language"] == l])
                         for l in LANGUAGES} for cat in sorted(set(categories.values()))}
    comparisons = {b: compare(cells, b, samples=samples) for b in LANGUAGES[1:]}
    category_comparisons = {cat: {b: compare([c for c in cells if c["category"] == cat], b, samples=samples)
                                  for b in LANGUAGES[1:]} for cat in by_category}
    common_categories = [cat for cat, comps in category_comparisons.items()
                         if all(v["correctness_and_efficiency_gate"] for v in comps.values())]
    accounting_complete = all(c["efficiency_eligible"] for c in cells) and complete
    general_gate = (accounting_complete and len(by_category) == 6 and not any(c["issues"] for c in cells)
                    and all(v["correctness_and_efficiency_gate"] for v in comparisons.values())
                    and len(common_categories) > 1)
    deviations = [
        "Time estimator difference: the operationalized sensitivity gate uses the median of per-task mean restricted times, with unsuccessful attempts set to 1200 seconds; this is not the literal preregistered accepted-only solution-time median",
        "Repair estimator difference: the operationalized sensitivity gate uses mean augmented repair burden, with unsuccessful attempts set to 4; this is not the literal preregistered actual public-repair count",
    ]
    if not complete:
        deviations.append("Incomplete design: observed tasks/cells differ from preregistered 30/270")
    if len(by_category) != 6:
        deviations.append("Observed category count differs from preregistered six")
    if any(c["infrastructure_error"] for c in cells):
        deviations.append("Infrastructure errors make affected paired task clusters unavailable")
    if any(c["unknown_usage"] or (not c["primary_eligible"] and not c["infrastructure_error"]) for c in cells):
        deviations.append("Unknown/unverified budgets exclude affected paired task clusters; known violations remain disqualified failures")
    issues = [{"task_id": c["task_id"], "language": c["language"], "repeat": c["repeat"], "issues": c["issues"]}
              for c in cells if c["issues"]]
    return {"methodology": {"expected_tasks": TASKS, "expected_repetitions": 3, "expected_cells": TASKS*9,
                "budget_seconds": SECONDS, "sampled_token_budget": TOKENS, "seed": SEED,
                "bootstrap_samples": samples, "ci": "two-sided 95% percentile task-cluster bootstrap",
                "unit": "task; average three repetitions within each language before resampling",
                "primary": "mean paired hidden full-task success-within-verified-budget difference",
                "time": "median of per-task mean restricted times; unsuccessful cells censored at 1200 seconds",
                "repairs": "mean augmented burden: accepted cell uses actual repairs; unsuccessful cell uses 4",
                "reasoning_tokens": "subset of output_tokens, never added to sampled output total",
                "model_provenance": "requested gpt-6.1-sol/high; JSONL does not return a backend-resolved model snapshot"},
            "design": {"complete": complete, "observed_tasks": len(tasks), "observed_cells": len(cells),
                       "observed_categories": len(by_category), "missing_cells_among_observed_tasks": missing,
                       "unidentified_missing_tasks": max(0,TASKS-len(tasks)), "efficiency_accounting_complete": accounting_complete},
            "deviations_or_limits": deviations, "record_issues": issues,
            "overall": summarize(cells), "by_language": by_language, "by_task": by_task,
            "by_category": by_category, "paired_comparisons": comparisons,
            "category_paired_comparisons": category_comparisons,
            "continuation_assessment": {"common_improving_categories": common_categories,
                "more_than_one_common_category": len(common_categories)>1,
                "operationalized_general_gate_established": bool(general_gate),
                "interpretation": ("Failure-penalized sensitivity gate met; independent execution audit remains required" if general_gate else "Failure-penalized sensitivity gate not established; favorable point estimates alone do not establish advantage")
                    + ". The literal preregistered accepted-only time / actual-repair threshold is not established by this gate"},
            "cells": cells}


def fmt(value, percent=False):
    if value is None:
        return "unavailable"
    return f"{value*100:.1f}%" if percent else f"{value:.2f}"


def estimate_text(result, percent=True):
    ci = result["ci95"]
    return f"{fmt(result['estimate'], percent)} ({fmt(ci[0], percent)} to {fmt(ci[1], percent)})" if ci else f"{fmt(result['estimate'], percent)} (CI unavailable)"


def markdown(result):
    d = result["design"]
    lines = ["# Controlled authoring experiment analysis", "",
        result["continuation_assessment"]["interpretation"] + ".", "",
        f"Observed {d['observed_cells']}/270 cells, {d['observed_tasks']}/30 tasks, and {d['observed_categories']}/6 categories. "
        "The JSON aggregate retains per-task, per-category and per-cell metrics without including source or fixture contents.", "",
        "## Methodology and limits", "",
        "Primary correctness is hidden full-task success within the verified common budget, not public-example pass rate. "
        "First-submission correctness is reported separately. Infrastructure failures are unobserved language outcomes; unknown transport usage cannot verify the token budget. "
        "Known token/time overruns remain disqualified failures in primary and efficiency metrics. An affected unknown-budget task is excluded as a whole from the relevant paired comparison, including all three repetitions in both compared languages. "
        "Observed raw correctness is descriptive and may include unverified-budget cells.", "",
        "Bootstrap seed is 20261017 with 10,000 resamples and two-sided 95% percentile intervals. Each draw resamples task clusters after averaging three repetitions per language. "
        "This retains within-task dependence; tasks, not hidden cases or 270 cells, are the units of inference. Complete-design comparisons contain 30 clusters; exclusions reduce that number. "
        "The task set is small and curated, and category estimates are exploratory; intervals have no multiplicity adjustment.", "",
        "Time starts when a worker begins authoring and stops when the public phase freezes. Queue delay and post-freeze hidden evaluation are excluded. "
        "Generation/transport time, public compiler time, public evaluator wall time, and hidden evaluation time are reported separately. "
        "Every unsuccessful non-infrastructure cell is administratively right-censored at 1200 seconds even if it failed public examples or hit a token/tool limit earlier. "
        "The reported restricted time is min(time-to-correct,1200), not a claim that failed cells eventually become correct. "
        "The paired time statistic is the median of per-task mean restricted times. Individual-cell restricted means/medians are also shown. "
        "Accepted-only times and repairs condition on success and can favor a language that solves only easier tasks; they cannot establish an efficiency advantage.", "",
        "Failures receive augmented repair burden 4 (beyond the three allowed repairs), so zero repairs followed by early failure is not rewarded. "
        "The repair comparison uses mean per-task augmented burden; accepted-only repair counts remain descriptive. "
        "A zero baseline denominator makes a reduction and its interval unidentified; undefined bootstrap draws are not silently discarded.", "",
        "Usage is counted once from transport attempts, falling back to submissions only when transport records are absent. "
        "output_tokens includes reasoning tokens; reasoning counts and cached input counts are subsets, not added to their parent totals. "
        "Known token totals are lower bounds when usage is missing. Requested model/configuration is gpt-6.1-sol/high; JSONL does not supply a backend-resolved model snapshot.", "",
        "The failure-penalized operationalized sensitivity gate requires a correctness difference CI with lower endpoint at least zero, plus a point reduction of at least 20% in restricted median time or 25% in augmented repairs with a CI strictly above zero benefit. "
        "These estimators differ from the preregistered accepted-only solution-time median and actual public-repair count: failures receive 1200 seconds and repair burden 4, and time is aggregated as a median of per-task means. "
        "The literal accepted-only time / actual-repair threshold is not established by this gate. Accepted-only and observed actual-repair statistics remain descriptive. "
        "A correctness CI extending below zero cannot establish strict no-regression. The general sensitivity gate requires both baselines, more than one common qualifying category, and complete eligible accounting. "
        "There is no inference here about runtime speed, production readiness, unrestricted IDE productivity, or contract-tooling usability.", "",
        "## Outcomes", "",
        "| Language | Verified first | Verified final | Budget-verifiable non-infra cells | Raw hidden final | Infra | Unknown usage | Public pass / hidden fail |", "| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |"]
    for lang, s in result["by_language"].items():
        lines.append(f"| {lang} | {s['verified_initial_success']} | {s['verified_final_success']} | {s['primary_eligible_records']} | {s['observed_final_correct']}/{s['records']} | {s['infrastructure_error']} | {s['unknown_usage']} | {s['public_pass_hidden_fail']} |")
    lines += ["", "## Time and repairs", "", "| Language | Restricted time mean / median (s) | Accepted time mean / median (s), n | Accepted repairs mean, n | Observed actual repairs mean, n | Augmented repairs mean |", "| --- | ---: | ---: | ---: | ---: | ---: |"]
    for lang, s in result["by_language"].items():
        t,a,r,b=(s[k] for k in ("restricted_time_to_correct_seconds","conditional_accepted_time_seconds","conditional_accepted_repairs","augmented_repair_burden"))
        observed = s["observed_repairs_all_records"]
        lines.append(f"| {lang} | {fmt(t['mean'])} / {fmt(t['median'])} | {fmt(a['mean'])} / {fmt(a['median'])}, {a['n']} | {fmt(r['mean'])}, {r['n']} | {fmt(observed['mean'])}, {observed['n']} | {fmt(b['mean'])} |")
    lines += ["", "## Paired Branchline comparisons", "", "Positive correctness differences and positive relative reductions favor Branchline. Intervals are 95% task-cluster intervals. Time and repair comparisons are failure-penalized sensitivity measures, not the literal preregistered accepted-only / actual-repair thresholds.", "", "| Baseline | Correctness clusters / efficiency clusters | Final success difference | Restricted median time reduction | Augmented repair reduction | No-regression established |", "| --- | ---: | --- | --- | --- | --- |"]
    excluded_notes = []
    for base,c in result["paired_comparisons"].items():
        lines.append(f"| {base} | {len(c['primary_task_ids'])} / {len(c['efficiency_task_ids'])} | {estimate_text(c['final_success_difference_branchline_minus_baseline'])} | {estimate_text(c['restricted_median_time_relative_reduction'])} | {estimate_text(c['augmented_mean_repair_relative_reduction'])} | {c['correctness_nonregression_established']} |")
        if c["excluded_tasks"]:
            excluded_notes.append(f"Excluded {base} task pairs: " + "; ".join(f"{t}: {', '.join(reasons)}" for t,reasons in c["excluded_tasks"].items()) + ".")
    for note in excluded_notes:
        lines += ["", note]
    lines += ["", "## Categories", "", "Counts below are verified final successes / budget-verifiable non-infrastructure records. Category bootstrap details and first-submission counts are in aggregate.json.", "", "| Category | Branchline | JSONata | Kotlin | Operationalized sensitivity gates vs both |", "| --- | ---: | ---: | ---: | --- |"]
    for cat, groups in result["by_category"].items():
        counts=[f"{groups[l]['verified_final_success']}/{groups[l]['primary_eligible_records']}" for l in LANGUAGES]
        gate=all(c["correctness_and_efficiency_gate"] for c in result["category_paired_comparisons"][cat].values())
        lines.append(f"| {cat} | " + " | ".join(counts) + f" | {gate} |")
    lines += ["", f"Common categories meeting the operationalized sensitivity gate: {', '.join(result['continuation_assessment']['common_improving_categories']) or 'none established'}.", "", "## Accounting and failure modes", "", "| Language | Public compile/envelope failures (submissions) | Time limit / token limit / tool cells | Input / sampled output / reasoning subset tokens | Generation / public compile / public evaluator wall / queue (total s) |", "| --- | ---: | ---: | ---: | ---: |"]
    for lang,s in result["by_language"].items():
        u=s["usage"]
        usage=" / ".join(str(u[k]["known_total"]) for k in ("input_tokens","output_tokens","reasoning_output_tokens"))
        timing=" / ".join(fmt(s[k]["total"]) for k in ("author_generation_seconds","public_compile_seconds","public_evaluator_wall_seconds","queue_seconds"))
        lines.append(f"| {lang} | {s['compile_fail_submissions']} | {s['time_limit_reached']} / {s['token_limit_reached']} / {s['tool_violation']} | {usage} | {timing} |")
    lines += ["", "A compiler/envelope failure includes a malformed or missing schema source as well as compile_ok=false diagnostics; it is not automatically a language compiler defect. Token totals above are known totals; a zero reasoning subset can mean the transport did not expose that subcount. Unknown usage cells and affected paired tasks are excluded as described above. Queue totals are shown only for audit and never added to authoring time.", "", "## Deviations and review requirements", ""]
    lines += ["- " + x for x in result["deviations_or_limits"]] or ["- No data-completeness or accounting deviation detected by this script."]
    if result["record_issues"]:
        lines.append(f"- {len(result['record_issues'])} record consistency issues require independent review; see aggregate.json.")
    lines += ["- Isolation and seal checks require the runner's independent audit artifacts; this analysis does not establish them from cell outcome records.", "- Backend model snapshot is unavailable; only the requested CLI model/configuration is recorded.", "- The parent reviewer must inspect preregistration deviations and execution audit before accepting any continuation conclusion.", ""]
    return "\n".join(lines)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, default=Path("/tmp/branchline-authoring-private/all-results.json"))
    parser.add_argument("--output-dir", type=Path, required=True)
    args=parser.parse_args()
    payload=args.input.read_bytes()
    result=analyze(json.loads(payload))
    result["input_provenance"]={"path":str(args.input.resolve()), "sha256":hashlib.sha256(payload).hexdigest()}
    args.output_dir.mkdir(parents=True, exist_ok=True)
    (args.output_dir/"aggregate.json").write_text(json.dumps(result, ensure_ascii=False, indent=2, allow_nan=False)+"\n")
    (args.output_dir/"report.md").write_text(markdown(result))
    print(json.dumps({"output_dir":str(args.output_dir.resolve()),"cells":result["design"]["observed_cells"],
                      "operationalized_general_gate_established":result["continuation_assessment"]["operationalized_general_gate_established"]}))


if __name__ == "__main__":
    main()
