#!/usr/bin/env python3
"""Non-scored adapter checks only. Never opens experiment tasks or hidden fixtures."""
import json
from pathlib import Path
import socket
import tempfile
from evaluate import REPO, RUNTIME, evaluate

SOURCES = {
    "branchline": "TRANSFORM Main { OUTPUT {value: input.value} }",
    "jsonata": '{"value": value}',
    "kotlin": 'fun transform(input: Any?): Any? = mapOf("value" to (input as Map<*, *>)["value"])',
}
VALUES = [{}, [], [0], [None], 0, None, False, "0",
          {"present": None, "list": [3, 1, 2], "nested": {"empty": []}},
          9007199254740991, {"decimal": 1.25}]


def main():
    RUNTIME.mkdir(parents=True, exist_ok=True)
    report = {}
    with tempfile.TemporaryDirectory(prefix="smoke-", dir=RUNTIME) as directory:
        work = Path(directory)
        fixtures = [{"input": {"value": value}, "expected": {"value": value}} for value in VALUES]
        fixtures += [{"input": {"value": {"present": None}}, "expected": {"value": {}}},
                     {"input": {"value": "0"}, "expected": {"value": 0}},
                     {"input": {"value": [2, 1]}, "expected": {"value": [1, 2]}},
                     {"input": {"value": False}, "expected": {"value": 0}}]
        cases = work / "smoke.json"
        cases.write_text(json.dumps(fixtures))
        for language, text in SOURCES.items():
            source = work / f"identity.{language}"
            source.write_text(text)
            output = work / f"{language}.json"
            assert evaluate(language, source, cases, output) == 0, output.read_text()
            result = json.loads(output.read_text())
            assert result["compile_ok"], result
            assert all(c["ok"] for c in result["cases"][:len(VALUES)]), result
            assert all(not c["ok"] for c in result["cases"][len(VALUES):]), result
            report[language] = {"identity_passes": len(VALUES), "deliberate_mismatches": 4}
            source.write_text({"branchline": "TRANSFORM Main { OUTPUT ??? }",
                               "jsonata": "($bad := )",
                               "kotlin": "fun transform(input: Any?): Any? = unknownIdentifier"}[language])
            assert evaluate(language, source, cases, output) == 0, output.read_text()
            invalid = json.loads(output.read_text())
            assert not invalid["compile_ok"] and invalid.get("error"), invalid
            report[language]["compile_error_detected"] = True
        # JSONata's absent root must not collapse into explicit JSON null.
        source = work / "undefined.jsonata"
        source.write_text("missing")
        cases.write_text('[{"input":{},"expected":null}]')
        output = work / "undefined.json"
        assert evaluate("jsonata", source, cases, output) == 0
        undefined = json.loads(output.read_text())
        assert undefined["compile_ok"] and not undefined["cases"][0]["ok"], undefined
        report["jsonata"]["undefined_is_not_null"] = True
        source.write_text("$")
        cases.write_text('[{"input":null,"expected":null}]')
        assert evaluate("jsonata", source, cases, output) == 0
        root_null = json.loads(output.read_text())
        assert root_null["compile_ok"] and root_null["cases"][0]["ok"], root_null
        report["jsonata"]["root_null_preserved"] = True
        # The candidate can read neither sibling submissions/private sentinels nor the checkout.
        with tempfile.TemporaryDirectory(prefix="other-evaluation-", dir=RUNTIME) as sibling:
            other = Path(sibling) / "other-submission.kt"
            other.write_text("SYNTHETIC_OTHER_SUBMISSION_SENTINEL")
            secret = RUNTIME / "isolation-hidden-sentinel.json"
            secret.write_text('{"sentinel":"SYNTHETIC_HIDDEN_SENTINEL"}')
            paths = [str(other), str(other.resolve()), str(secret), str(secret.resolve()),
                     str(REPO / "development/experiments/authoring-2026-10-17/evaluator/evaluate.py")]
            with socket.socket() as listener:
                listener.bind(("127.0.0.1", 0))
                listener.listen(1)
                port = listener.getsockname()[1]
                source.write_text('''fun transform(input: Any?): Any? {
    val data = input as Map<*, *>
    val blocked = (data["paths"] as List<*>).map { path ->
        try { java.io.File(path as String).readText(); false }
        catch (_: java.io.IOException) { true }
    }
    val networkBlocked = try {
        java.net.Socket().use { it.connect(java.net.InetSocketAddress("127.0.0.1", (data["port"] as Number).toInt()), 500) }
        false
    } catch (_: java.io.IOException) { true }
    val caseFileUnavailable = !java.io.File("cases.json").exists()
    val environmentClean = System.getenv().keys.all {
        it in setOf("PATH", "TMPDIR", "LANG", "__CF_USER_TEXT_ENCODING")
    }
    return mapOf("blocked" to blocked, "networkBlocked" to networkBlocked,
        "caseFileUnavailable" to caseFileUnavailable, "environmentClean" to environmentClean)
}''')
                cases.write_text(json.dumps([{"input": {"paths": paths, "port": port},
                    "expected": {"blocked": [True] * len(paths), "networkBlocked": True,
                                 "caseFileUnavailable": True, "environmentClean": True}}]))
                assert evaluate("kotlin", source, cases, output) == 0, output.read_text()
                isolated = json.loads(output.read_text())
                assert isolated["compile_ok"] and isolated["cases"][0]["ok"], isolated
            secret.unlink()
        report["kotlin"]["private_siblings_repo_and_network_denied"] = True
        report["kotlin"]["copied_expected_file_deleted_before_execution"] = True
        report["kotlin"]["inherited_environment_removed"] = True
    path = RUNTIME / "selftest-report.json"
    path.write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
