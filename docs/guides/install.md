---
title: Install Branchline
---

# Install Branchline

This guide gets Branchline running locally so you can execute programs from the CLI or IDE. All commands use the Gradle wrapper—avoid system Gradle.

## Prerequisites
- JDK (use Gradle toolchains if not installed globally).
- Node.js (required for the JS CLI and playground development).
- Git, bash/zsh, and internet access for dependency downloads on first run.

## Clone and verify toolchain
```bash
git clone https://github.com/ehlyzov/branchline-public.git
cd branchline-public
./gradlew --version          # checks JDK/toolchain
./gradlew :cli:runBl --help  # confirms CLI wiring
```

## JVM CLI
- Run a script:  
  ```bash
  ./gradlew :cli:runBl --args "path/to/program.bl --input sample.json"
  ```
- Compile to bytecode:  
  ```bash
  ./gradlew :cli:runBlc --args "path/to/program.bl --output build/program.blc"
  ```
- Execute bytecode on the VM:  
  ```bash
  ./gradlew :cli:runBlvm --args "build/program.blc --input sample.json"
  ```
- XML input: add `--input-format xml`.

## Node CLI
- Run with the Node runtime:  
  ```bash
  ./gradlew :cli:jsNodeProductionRun --args="path/to/program.bl --input sample.json"
  ```
- Package a distributable tarball:  
  ```bash
  ./gradlew :cli:packageJsCli
  # outputs cli/build/distributions/branchline-cli-js-<version>.tgz
  ```

## Verify with a tiny program
Create `hello.bl`:
```branchline
SOURCE msg;
TRANSFORM Hello {
    LET greet = "Hello, " + msg.name;
    OUTPUT { greeting: greet };
}
```
Run on JVM:
```bash
./gradlew :cli:runBl --args "hello.bl --input hello.json"
```
With `hello.json`:
```json
{ "name": "Branchline" }
```
Expected output:
```json
{ "greeting": "Hello, Branchline" }
```

## Troubleshooting
- **JDK missing or wrong version**: install one or enable Gradle toolchains; rerun `./gradlew --version`.
- **Node not found**: install Node 18+ for JS CLI and playground dev.
- **Gradle daemon issues**: `./gradlew --stop` then retry.
- **Proxy/SSL errors**: ensure corporate proxy settings are configured for Gradle.

Next: follow [First steps](first-steps.md) to learn the language and try more examples.
