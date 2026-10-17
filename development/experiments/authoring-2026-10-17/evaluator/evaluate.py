#!/usr/bin/env python3
"""Local three-language evaluator. No author/model calls; no scored fixture discovery."""
import argparse
import fcntl
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import time

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[3]
RUNTIME = Path(os.environ.get("AUTHORING_RUNTIME", "/tmp/branchline-authoring-private/runtime"))
COMPILE_TIMEOUT = 60
EXECUTE_TIMEOUT = 30
PRELUDE = "import java.math.*\nimport kotlin.math.*\n"


def command(argv, *, timeout, cwd=None, env=None):
    # Capture author stdout separately from the machine-readable result file.
    return subprocess.run(argv, cwd=cwd, text=True, stdout=subprocess.PIPE,
                          stderr=subprocess.STDOUT, timeout=timeout, env=env)


def compiler_command(config, source, destination):
    return [config["java"], "-Xmx768m", "-cp", os.pathsep.join(config["compiler_classpath"]),
            "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-no-stdlib", "-no-reflect",
            "-jvm-target", "21", "-classpath", os.pathsep.join(config["classpath"]),
            "-d", str(destination), str(source)]


def sandbox_command(config, argv, work, tag):
    """An allowlist shared by the compiler and candidate JVM; siblings stay private."""
    sandbox = Path("/usr/bin/sandbox-exec")
    if not sandbox.is_file():
        raise RuntimeError("macOS sandbox-exec is required; refusing unsandboxed execution")
    original_work = str(work)
    work = work.resolve()
    roots = ["/System", "/usr", "/Library", "/dev", "/etc", "/private/etc",
             "/private/var/db/timezone", "/private/preboot", "/private/var/db/dyld",
             str(work), original_work, str(RUNTIME / "host"), str((RUNTIME / "host").resolve())]
    files = config["classpath"] + config["compiler_classpath"]
    rules = [f"(subpath {json.dumps(path)})" for path in roots]
    rules += [f"(literal {json.dumps(path)})" for path in ("/", "/private", "/private/var", "/private/var/db")]
    rules += [f"(literal {json.dumps(str(Path(path).resolve()))})" for path in files]
    rules += [f"(literal {json.dumps(path)})" for path in files]
    # Java canonicalizes classpath entries, requiring metadata on their parent directories.
    ancestors = {str(parent) for path in [*files, str(work), str(RUNTIME / "host")]
                 for parent in Path(path).resolve().parents}
    ancestors |= {str(parent) for path in [*files, original_work, str(RUNTIME / "host")]
                  for parent in Path(path).parents}
    rules += [f"(literal {json.dumps(path)})" for path in sorted(ancestors)]
    profile = work / f"{tag}.sb"
    profile.write_text("\n".join([
        "(version 1)", "(allow default)", "(deny network*)",
        "(deny file-read* (require-not (require-any " + " ".join(rules) + ")))",
        f"(deny file-write* (require-not (subpath {json.dumps(str(work))})))",
        f"(deny process-exec (require-not (literal {json.dumps(str(Path(config['java']).resolve()))})))",
    ]) + "\n")
    # Keep Java scratch files inside the only writable directory. Disable attach/perf files.
    argv = [argv[0], f"-Djava.io.tmpdir={work}", "-XX:+DisableAttachMechanism",
            "-XX:-UsePerfData"] + argv[1:]
    return [str(sandbox), "-f", str(profile), *argv]


def prepare():
    RUNTIME.mkdir(parents=True, exist_ok=True)
    with (RUNTIME / "prepare.lock").open("w") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        config_path = RUNTIME / "config.json"
        if not config_path.exists():
            build = command([str(REPO / "gradlew"), "--console=plain", "-I",
                             str(HERE / "runtime.init.gradle"), "authoringEvaluatorRuntime",
                             f"-PauthoringRuntimeOutput={config_path}"], timeout=300, cwd=REPO)
            if build.returncode:
                raise RuntimeError("Runtime bootstrap failed:\n" + build.stdout[-12000:])
        config = json.loads(config_path.read_text())
        if config.get("kotlin_version") != "2.3.0" or config.get("jsonata_version") != "0.9.10":
            raise RuntimeError("Unexpected engine versions in runtime config")
        for item in config["classpath"] + config["compiler_classpath"]:
            if not Path(item).exists():
                raise RuntimeError(f"Runtime dependency missing: {item}")
        if not config.get("frozen_interpreter"):
            # Freeze the locally accepted interpreter instead of following later checkout builds.
            for index, item in enumerate(config["classpath"]):
                if "/interpreter/build/libs/" in item:
                    frozen = RUNTIME / "accepted-interpreter.jar"
                    shutil.copyfile(item, frozen)
                    config["classpath"][index] = str(frozen)
                    config["frozen_interpreter"] = str(frozen)
                    break
            if not config.get("frozen_interpreter"):
                raise RuntimeError("Could not identify the local interpreter artifact")
            config["artifact_sha256"] = {item: hashlib.sha256(Path(item).read_bytes()).hexdigest()
                                          for item in config["classpath"] + config["compiler_classpath"]}
            config_path.write_text(json.dumps(config, indent=2) + "\n")
        frozen = config["frozen_interpreter"]
        if hashlib.sha256(Path(frozen).read_bytes()).hexdigest() != config["artifact_sha256"][frozen]:
            raise RuntimeError("Frozen interpreter artifact changed")
        digest = hashlib.sha256((HERE / "EvaluatorHost.kt").read_bytes() +
                                config_path.read_bytes())
        for item in config["classpath"]:
            stat = Path(item).stat()
            digest.update(f"{item}:{stat.st_mtime_ns}:{stat.st_size}".encode())
        fingerprint = digest.hexdigest()
        stamp = RUNTIME / "host.sha256"
        host = RUNTIME / "host"
        if not stamp.exists() or stamp.read_text() != fingerprint:
            host.mkdir(exist_ok=True)
            compiled = command(compiler_command(config, HERE / "EvaluatorHost.kt", host),
                               timeout=COMPILE_TIMEOUT)
            if compiled.returncode:
                raise RuntimeError("Evaluator host compilation failed:\n" + compiled.stdout[-12000:])
            stamp.write_text(fingerprint)
        return config


def failure(error, *, compile_ok=False, compile_seconds=0.0, execute_seconds=0.0, count=0):
    return {"compile_ok": compile_ok,
            "cases": [{"ok": False, "actual": None, "error": error} for _ in range(count)],
            "error": error, "compile_seconds": compile_seconds, "execute_seconds": execute_seconds}


def evaluate(language, source, cases, output):
    """Return 0 for evaluated submissions (including failures), 2 for infrastructure errors."""
    output = Path(output).resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    try:
        source = Path(source).resolve()
        cases = Path(cases).resolve()
        # Validation only; comparison and lossless parsing happen in Jackson.
        fixtures = json.loads(cases.read_text())
        if not isinstance(fixtures, list) or any(not isinstance(c, dict) or
                "input" not in c or "expected" not in c for c in fixtures):
            raise ValueError("Cases must be an array of {input,expected} objects")
        config = prepare()
        with tempfile.TemporaryDirectory(prefix="evaluation-", dir=RUNTIME) as working:
            work = Path(working)
            candidate_env = {"PATH": "/usr/bin:/bin", "TMPDIR": str(work), "LANG": "en_US.UTF-8"}
            local_source = work / "submission.source"
            local_cases = work / "cases.json"
            shutil.copyfile(source, local_source)
            shutil.copyfile(cases, local_cases)
            extra_compile = 0.0
            classes = work / "solution"
            classes.mkdir()
            if language == "kotlin":
                solution = work / "Solution.kt"
                solution.write_text(PRELUDE + local_source.read_text())
                started = time.monotonic()
                try:
                    compiled = command(sandbox_command(config,
                                       compiler_command(config, solution, classes), work, "compiler"),
                                       timeout=COMPILE_TIMEOUT, cwd=work, env=candidate_env)
                except subprocess.TimeoutExpired:
                    result = failure("Kotlin compilation exceeded 60 seconds",
                                     compile_seconds=time.monotonic() - started)
                    output.write_text(json.dumps(result))
                    return 0
                extra_compile = time.monotonic() - started
                if compiled.returncode:
                    diagnostic = compiled.stdout.replace(str(solution), "Solution.kt")[-16000:]
                    result = failure(diagnostic, compile_seconds=extra_compile)
                    output.write_text(json.dumps(result))
                    return 0
            host_output = work / "result.json"
            cp = [str(RUNTIME / "host"), str(classes)] + config["classpath"]
            argv = [config["java"], "-Xmx512m", "-cp", os.pathsep.join(cp),
                    "EvaluatorHostKt", language, str(local_source), str(local_cases), str(host_output), str(extra_compile)]
            started = time.monotonic()
            try:
                executed = command(sandbox_command(config, argv, work, "candidate"),
                                   timeout=EXECUTE_TIMEOUT, cwd=work, env=candidate_env)
            except subprocess.TimeoutExpired:
                result = failure("Evaluation exceeded 30 seconds", compile_ok=True,
                                 compile_seconds=extra_compile,
                                 execute_seconds=time.monotonic() - started, count=len(fixtures))
                output.write_text(json.dumps(result))
                return 0
            if not host_output.exists():
                raise RuntimeError("Evaluator JVM did not return a result:\n" + executed.stdout[-12000:])
            # Preserve raw JSON numeric lexemes from Jackson; Python must not round-trip actual values.
            raw = host_output.read_text()
            json.loads(raw)  # Validate the envelope without reserializing its numbers.
            output.write_text(raw)
            return 0
    except Exception as error:
        result = failure(f"Infrastructure error: {type(error).__name__}: {error}")
        result["infrastructure_error"] = True
        output.write_text(json.dumps(result))
        return 2


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--language", choices=["branchline", "jsonata", "kotlin"])
    parser.add_argument("--source", type=Path)
    parser.add_argument("--cases", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--prepare", action="store_true", help="Bootstrap pinned local runtime only")
    args = parser.parse_args()
    if args.prepare:
        config = prepare()
        print(json.dumps({k: config[k] for k in ("kotlin_version", "jsonata_version", "jackson_version")}))
        return 0
    if any(value is None for value in (args.language, args.source, args.cases, args.output)):
        parser.error("--language, --source, --cases, and --output are required")
    return evaluate(args.language, args.source, args.cases, args.output)


if __name__ == "__main__":
    sys.exit(main())
