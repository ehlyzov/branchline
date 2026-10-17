from pathlib import Path
import subprocess, tempfile, json
root = Path(__file__).resolve().parents[3]
ev = Path(__file__).resolve().parent
work = Path(tempfile.mkdtemp(prefix="branchline-release-oracles-"))
jar = root / "cli/build/libs/branchline-cli-0.11.0-alpha.1-all.jar"
node = root / "cli/build/cliJsPackage/bin/bl.cjs"
results = []
for case in json.loads((ev / "acceptance-cases.json").read_text()):
    src = work / (case["id"] + ".bl")
    inp = work / (case["id"] + ".json")
    src.write_text(case["program"])
    inp.write_text(json.dumps(case["input"]))
    for engine, launcher in [("jvm", ["java", "-jar", str(jar)]), ("node", ["node", str(node)])]:
        result = subprocess.run(launcher + [str(src), "--input", str(inp), "--output-format", "json-compact"], text=True, capture_output=True)
        assert result.returncode == 0, (case["id"], engine, result.stderr)
        assert json.loads(result.stdout) == case["expected"], (case, engine, result.stdout)
        results.append({"case": case["id"], "engine": engine, "output": json.loads(result.stdout)})
(ev / "independent-cli-results.json").write_text(json.dumps(results, indent=2) + "\n")
print("PASS:", len(results), "independent JVM/Node outputs")
