---
title: Hello, Transform
---

# Hello, Transform

Write a minimal transform that reads input and emits a new object.

## Goal
Turn one input field into a greeting.

## Program
```branchline
TRANSFORM Hello {
    OUTPUT { greeting: "Hello, " + input.name };
}
```

## Input
```json
{ "name": "Dana" }
```

## Output
```json
{ "greeting": "Hello, Dana" }
```

## Try it in the Playground
- [hello-transform](../playground.md?example=hello-transform){ target="_blank" }

## Next steps
Continue to [Paths and Reshaping](paths-and-reshaping.md).
