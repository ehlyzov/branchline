---
title: Getting Started
---

# Getting Started

A minimal path from code to output.

## 1) Write a transform
Create `hello.bl`:

```branchline
TRANSFORM Hello {
    LET greet = "Hello, " + input.name;
    OUTPUT { greeting: greet };
}
```

## 2) Run it locally
```bash
./gradlew :cli:runBl --args "hello.bl --input sample.json"
```

## 3) Try it in the Playground
Open [hello-transform](../playground.md?example=hello-transform){ target="_blank" } and edit the program or input.

## Next steps
- Learn the syntax in the [Tour of Branchline](../learn/index.md).
- Use [TRY/CATCH](try-catch.md) to keep pipelines resilient.
- Explore the [Standard Library](../language/std-core.md).
