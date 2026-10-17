"""Compile ordinary, rejected preview and explicitly opted-in consumers with Kotlin 2.3.0."""
from pathlib import Path
import subprocess, tempfile, json, os
root = Path(__file__).resolve().parents[3]
evidence = Path(__file__).resolve().parent
work = Path(tempfile.mkdtemp(prefix="branchline-optin-"))
cache = Path.home() / ".gradle/caches/modules-2/files-2.1"
compiler = next((cache / "org.jetbrains.kotlin/kotlin-compiler-embeddable/2.3.0").rglob("*.jar"))
script = next((cache / "org.jetbrains.kotlin/kotlin-script-runtime/2.3.0").rglob("*.jar"))
annotations = next((cache / "org.jetbrains/annotations").rglob("*.jar"))
jar = root / "cli/build/libs/branchline-cli-0.11.0-alpha.1-all.jar"
cp = os.pathsep.join(map(str, [compiler, script, annotations, jar]))
common = "import io.github.ehlyzov.branchline.vm.*\n"
cases = {
    "ordinary": common + "fun run(code: Bytecode) = VM().execute(code)\n",
    "preview_unmarked": common + "fun run(code: Bytecode) = VM().prepareBlocks(code)\n",
    "preview_optin": common + "@OptIn(ExperimentalVMApi::class)\nfun run(code: Bytecode) = VM().prepareBlocks(code)\n",
}
results = []
for name, source in cases.items():
    path = work / (name + ".kt")
    path.write_text(source)
    cmd = ["java", "-cp", cp, "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-no-stdlib", "-no-reflect", "-jvm-target", "21", "-classpath", str(jar), "-d", str(work / name), str(path)]
    result = subprocess.run(cmd, text=True, capture_output=True)
    (evidence / (name + "-compiler.log")).write_text(result.stdout + result.stderr)
    results.append({"case": name, "exit": result.returncode})
    if name == "preview_unmarked":
        assert result.returncode != 0 and "unstable vm alpha preview" in result.stderr.lower(), result.stderr
    else:
        assert result.returncode == 0, result.stderr
(evidence / "optin-acceptance.json").write_text(json.dumps(results, indent=2) + "\n")
print(results)
