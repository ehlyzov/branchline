"""Exercise workflow shell branches without Gradle, remote reads, or mutations.

Uses the platform Ruby YAML parser to avoid adding a Python dependency.
Run from repository root: python3 development/evidence/release-0.11.0-alpha.1/workflow-context-check.py
"""
import json
import os
from pathlib import Path
import subprocess
import tempfile

WORKFLOWS = [".github/workflows/release-artifacts.yml", ".github/workflows/deploy.yml"]
data = json.loads(subprocess.check_output([
    "ruby", "-ryaml", "-rjson", "-e",
    "puts JSON.generate(ARGV.to_h { |p| [p, YAML.load_file(p)] })", *WORKFLOWS,
], text=True))
for path, workflow in data.items():
    for job in workflow["jobs"].values():
        for step in job.get("steps", []):
            if "run" in step:
                subprocess.run(["bash", "-n"], input=step["run"], text=True, check=True)
    print(f"PASS YAML and shell syntax: {path}")
steps = data[WORKFLOWS[0]]["jobs"]["build-and-upload"]["steps"]
context = next(s["run"] for s in steps if s.get("id") == "release_context")
gate = next(s["run"] for s in steps if s.get("id") == "deploy_gate")
deploy_context = next(s["run"] for s in data[WORKFLOWS[1]]["jobs"]["build"]["steps"]
                      if s.get("name") == "Bind deployment metadata to checkout")
sha = "a" * 40
with tempfile.TemporaryDirectory(prefix="branchline-workflow-") as directory:
    root = Path(directory)
    git = root / "git"
    git.write_text('''#!/bin/bash
case "$1 $2" in
  "rev-parse HEAD") echo "$MOCK_SHA";;
  "show-ref --verify") [[ -n "$MOCK_TAG_SHA" ]];;
  rev-parse*) echo "$MOCK_TAG_SHA";;
  *) echo "Unexpected git command: $*" >&2; exit 1;;
esac
''')
    git.chmod(0o755)
    gradle = root / "gradlew"
    gradle.write_text('#!/bin/bash\necho 0.11.0-alpha.1\n')
    gradle.chmod(0o755)
    cases = [
        ("manual full defaults", {}, 0, "v0.11.0-alpha.1", "true"),
        ("manual full explicit tag", {"DISPATCH_TAG": "v0.11.0-alpha.1", "MOCK_TAG_SHA": sha, "DISPATCH_RUN_JMH": "false"}, 0, "v0.11.0-alpha.1", "true"),
        ("wrong version tag rejected", {"DISPATCH_TAG": "v9.0.0"}, 1, None, None),
        ("wrong tag commit rejected", {"DISPATCH_TAG": "v0.11.0-alpha.1", "MOCK_TAG_SHA": "b" * 40}, 1, None, None),
        ("unsafe tag rejected", {"DISPATCH_TAG": "bad\ntag"}, 1, None, None),
        ("multiline benchmark input rejected", {"DISPATCH_JSONATA_CASE_IDS": "case\nBUILD_SHA=bad"}, 1, None, None),
        ("no work rejected", {"DISPATCH_FULL_RELEASE": "false", "DISPATCH_RUN_JMH": "false"}, 1, None, None),
        ("manual benchmark only", {"DISPATCH_FULL_RELEASE": "false"}, 0, "snapshot-aaaaaaa", "true"),
        ("manual JSONata only", {"DISPATCH_FULL_RELEASE": "false", "DISPATCH_RUN_JMH": "false", "DISPATCH_RUN_JSONATA": "true"}, 0, "snapshot-aaaaaaa", "true"),
        ("push performance", {"GITHUB_EVENT_NAME": "push", "HEAD_COMMIT_MESSAGE": "+performance"}, 0, "perf-aaaaaaa", "true"),
        ("push JSONata", {"GITHUB_EVENT_NAME": "push", "HEAD_COMMIT_MESSAGE": "+jsonata"}, 0, "perf-aaaaaaa", "true"),
        ("release event", {"GITHUB_EVENT_NAME": "release", "RELEASE_TAG": "v0.11.0-alpha.1", "MOCK_TAG_SHA": sha}, 0, "v0.11.0-alpha.1", "true"),
    ]
    for name, overrides, expected_code, expected_tag, expected_deploy in cases:
        env_file, output_file = root / "env", root / "output"
        env_file.write_text("")
        output_file.write_text("")
        env = dict(os.environ, PATH=f"{root}:{os.environ['PATH']}", MOCK_SHA=sha,
                   MOCK_TAG_SHA="", GITHUB_EVENT_NAME="workflow_dispatch", GITHUB_SHA=sha,
                   GITHUB_ENV=str(env_file), GITHUB_OUTPUT=str(output_file))
        env.update(overrides)
        result = subprocess.run(["bash", "-c", context], cwd=root, env=env, text=True, capture_output=True)
        assert result.returncode == expected_code, (name, result.stdout, result.stderr)
        if expected_code == 0:
            resolved = dict(line.split("=", 1) for line in env_file.read_text().splitlines())
            assert resolved["TAG_NAME"] == expected_tag, name
            assert resolved["BUILD_SHA"] == sha, name
            assert resolved["PACKAGE_VERSION"] == "0.11.0-alpha.1", name
            assert resolved["RELEASE_PRERELEASE"] == "true", name
            if name == "push JSONata":
                assert resolved["RUN_JSONATA"] == "true"
            env.update(resolved)
            subprocess.run(["bash", "-c", gate], env=env, text=True, check=True)
            assert f"deploy_pages={expected_deploy}" in output_file.read_text(), name
        print(f"PASS {name}")
    (root / "docs").mkdir()
    deploy_cases = [
        ("deployment with exact SHA/version", sha, "0.11.0-alpha.1", 0),
        ("deployment standalone selected ref", "main", "", 0),
        ("deployment wrong SHA rejected", "b" * 40, "0.11.0-alpha.1", 1),
        ("deployment wrong version rejected", sha, "0.10.0", 1),
    ]
    for name, requested, version, expected in deploy_cases:
        env = dict(os.environ, PATH=f"{root}:{os.environ['PATH']}", MOCK_SHA=sha,
                   GITHUB_ENV=str(root / "env"), GITHUB_REPOSITORY="example/branchline",
                   GITHUB_RUN_ID="123", REQUESTED_REF=requested, EXPECTED_VERSION=version)
        result = subprocess.run(["bash", "-c", deploy_context], cwd=root, env=env, text=True, capture_output=True)
        assert result.returncode == expected, (name, result.stdout, result.stderr)
        if expected == 0:
            text = (root / "docs/build-info.md").read_text()
            assert sha in text and "v0.11.0-alpha.1" in text, name
        print(f"PASS {name}")
print(f"PASS {len(cases)} release-context and {len(deploy_cases)} deployment cases")
