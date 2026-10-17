#!/usr/bin/env python3
"""Read-only lexical contamination screen of completed authoring experiment cells.

Flags require review; absence of a flag is not proof of arbitrary-code safety.
Never invokes submissions, authors, the evaluator, or scored engines.
"""
import argparse
from collections import Counter
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re

SEAL_PATH = Path(__file__).resolve().parent.parent / "seal.json"
RULES = {
    "filesystem": r"\b(?:java\s*\.\s*io|java\s*\.\s*nio|kotlin\s*\.\s*io)\b|\b(?:File|Files|Path|Paths|FileSystems|FileInputStream|FileOutputStream|RandomAccessFile|readText|readBytes|writeText|writeBytes|readLines|readAllLines|readAllBytes|listFiles|walkTopDown|bufferedReader|inputStream|outputStream)\b",
    "network": r"\b(?:java\s*\.\s*net|javax\s*\.\s*net)\b|\b(?:Socket|ServerSocket|DatagramSocket|URLConnection|HttpClient|HttpRequest|InetAddress|InetSocketAddress)\b|https?://",
    "reflection_or_classloader": r"\b(?:java\s*\.\s*lang\s*\.\s*reflect|kotlin\s*\.\s*reflect|ClassLoader|URLClassLoader|MethodHandles|Unsafe|Instrumentation|ManagementFactory|MBeanServer)\b|\bClass\s*\.\s*forName\b|\b(?:getClass|getDeclared\w*|getMethod|getField|setAccessible|trySetAccessible|getResource|getResourceAsStream|javaClass)\b|::\s*class\b|\bclass\s*\.\s*java\b|\b(?:sun\s*\.\s*misc|jdk\s*\.\s*internal)\b",
    "process_or_environment": r"\b(?:ProcessBuilder|Runtime|Thread|ProcessHandle)\b|\bSystem\s*\.\s*(?:getenv|getProperty|getProperties|setProperty|setProperties|exit|load|loadLibrary|setSecurityManager|out|err|`in`)\b|\b(?:exec|halt|addShutdownHook)\s*\(",
    "other_engine_or_dynamic_eval": r"\b(?:io\s*\.\s*github\s*\.\s*ehlyzov|com\s*\.\s*dashjoin|javax\s*\.\s*script|javax\s*\.\s*tools|kotlin\s*\.\s*script)\b|\b(?:Jsonata|ScriptEngine|ScriptEngineManager|K2JVMCompiler|JavaCompiler|ToolProvider)\b|\$eval\s*\(|@file\s*:\s*(?:DependsOn|Repository)",
    "host_protocol_or_private_path": r"EvaluatorHost|compile_ok|compile_seconds|execute_seconds|selected_submission|infrastructure_error|(?:hidden|cases|result)\.json|submission-\d|branchline-authoring-private|/private/tmp/|/tmp/|/Users/|\.codex/|sun\.java\.command",
    "encoded_or_obfuscated_payload": r"\b(?:Base64|base64|decodeToString|decodeBase64|ObjectInputStream|ObjectOutputStream|SerializedLambda|CharArray|ByteArray)\b|(?:\\u[0-9a-fA-F]{4}){3,}|(?:\\x[0-9a-fA-F]{2}){4,}",
}
COMPILED = {name: re.compile(pattern) for name, pattern in RULES.items()}
QUOTED = re.compile(r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'')


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def strings(value):
    if isinstance(value, str):
        yield value
    elif isinstance(value, list):
        for item in value:
            yield from strings(item)
    elif isinstance(value, dict):
        for key, item in value.items():
            yield key
            yield from strings(item)


def scan(source, hidden_only):
    flags = []
    # Decode escapes for screening only; never execute or import candidate source.
    normalized = re.sub(r"\\u([0-9a-fA-F]{4})", lambda m: chr(int(m.group(1), 16)), source)
    for rule, pattern in COMPILED.items():
        for match in pattern.finditer(normalized):
            flags.append({"rule": rule, "line": normalized.count("\n", 0, match.start()) + 1,
                          "token": match.group(0)[:120]})
    for match in QUOTED.finditer(source):
        text = match.group(0)
        if len(text) > 512:
            flags.append({"rule": "long_literal_review", "line": source.count("\n", 0, match.start()) + 1,
                          "literal_chars": len(text)})
        # Hidden-only strings are suspicious evidence, not an automatic misconduct finding.
        body = text[1:-1]
        if body in hidden_only:
            flags.append({"rule": "hidden_only_literal_review", "line": source.count("\n", 0, match.start()) + 1,
                          "literal_sha256": hashlib.sha256(body.encode()).hexdigest(),
                          "literal_chars": len(body)})
    return flags


def audit(runs, tasks, hidden, seal_digest=None):
    seal_digest = seal_digest or sha(SEAL_PATH)
    task_map = {task["id"]: task for task in json.loads(tasks.read_text())}
    hidden_map = json.loads(hidden.read_text())
    hidden_only = {}
    for ident, task in task_map.items():
        visible = task["spec"] + json.dumps(task["public"], ensure_ascii=False)
        hidden_only[ident] = {value for value in strings(hidden_map[ident])
                              if len(value) >= 6 and value not in visible}
    cells = []
    skipped = []
    for result_path in sorted(runs.glob("*/result.json")):
        try:
            result = json.loads(result_path.read_text())
        except (OSError, json.JSONDecodeError) as error:
            skipped.append({"path": str(result_path), "reason": type(error).__name__})
            continue
        if "finished_unix" not in result:
            skipped.append({"path": str(result_path), "reason": "not completed"})
            continue
        ident = result.get("task_id")
        issues = []
        if result.get("seal_sha256") != seal_digest:
            issues.append({"rule": "seal_mismatch"})
        if ident not in hidden_only:
            issues.append({"rule": "unknown_task"})
        for index, submission in enumerate(result.get("submissions", [])):
            if submission.get("tool_events"):
                issues.append({"rule": "author_tool_events", "submission_index": submission.get("index", index)})
        for attempt in result.get("transport_attempts", []):
            if attempt.get("tool_events"):
                issues.append({"rule": "transport_tool_events", "submission_index": attempt.get("submission")})
        sources = []
        for path in sorted(result_path.parent.glob("submission-*.source")):
            source = path.read_text()
            sources.append({"path": str(path), "sha256": sha(path), "bytes": path.stat().st_size,
                            "lines": len(source.splitlines()),
                            "flags": scan(source, hidden_only.get(ident, set()))})
        expected = {submission.get("index", index) for index, submission in enumerate(result.get("submissions", []))}
        present = {int(path.stem.split("-")[-1]) for path in result_path.parent.glob("submission-*.source")}
        if expected - present:
            issues.append({"rule": "missing_submission_source", "indices": sorted(expected - present)})
        cells.append({"cell": result_path.parent.name, "language": result.get("language"),
                      "result_sha256": sha(result_path), "source_count": len(sources),
                      "issues": issues, "sources": sources})
    counts = Counter(flag["rule"] for cell in cells for source in cell["sources"] for flag in source["flags"])
    counts.update(issue["rule"] for cell in cells for issue in cell["issues"])
    return {"generated_utc": datetime.now(timezone.utc).isoformat(), "sealed_manifest_sha256": seal_digest,
            "scope": "Completed cells only; lexical screen plus hidden-only literal screen; no business-logic grading",
            "completed_cells": len(cells), "expected_cells": 270,
            "source_count": sum(cell["source_count"] for cell in cells),
            "cells_by_language": dict(Counter(cell["language"] for cell in cells)),
            "flag_counts": dict(sorted(counts.items())),
            "flagged_cells": [cell["cell"] for cell in cells if cell["issues"] or
                              any(source["flags"] for source in cell["sources"])],
            "unconfirmed_flags_require_review": True, "skipped": skipped, "cells": cells}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--runs", type=Path, default=Path("/tmp/branchline-authoring-private/runs"))
    parser.add_argument("--tasks", type=Path, default=Path("/tmp/branchline-authoring-public/tasks.json"))
    parser.add_argument("--hidden", type=Path, default=Path("/tmp/branchline-authoring-private/hidden.json"))
    parser.add_argument("--seal", type=Path, default=SEAL_PATH)
    parser.add_argument("--output", type=Path, default=Path(__file__).with_name("completed-source-audit.json"))
    args = parser.parse_args()
    report = audit(args.runs, args.tasks, args.hidden, sha(args.seal))
    args.output.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n")
    print(json.dumps({key: report[key] for key in ["completed_cells", "source_count", "cells_by_language",
                                                 "flag_counts", "flagged_cells"]}, indent=2))


if __name__ == "__main__":
    main()
