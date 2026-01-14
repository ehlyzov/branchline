---
title: Getting Started
---

# Getting Started

Write a minimal transform and run it locally or in the Playground.

## Step 1: Write a transform
Create `hello.bl`:

```branchline
TRANSFORM Hello {
    LET greet = "Hello, " + input.name;
    OUTPUT { greeting: greet };
}
```

## Step 2: Run locally
```bash
./gradlew :cli:runBl --args "hello.bl --input sample.json"
```

## Step 3: Try in the Playground
Open [customer-profile](../playground.md?example=customer-profile){ target="_blank" } and edit the program or input.

## Next steps
- Add a new field to the output and rerun.
- Read the [Language Overview](../language/index.md) for syntax details.
- Explore [Standard Library](../language/std-core.md) pages for reusable helpers.
