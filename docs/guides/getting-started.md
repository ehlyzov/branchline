---
title: Getting Started
---

# Getting Started

Turn an order into a shipment payload, change the input, then run your edited
files locally. You will get the same JSON result in the browser and terminal.

## Install Branchline

On **macOS or Linux**, run this command in your terminal. It downloads Branchline
and its private Java runtime; **you do not need Java or Node installed**. No `sudo` needed.

--8<-- "includes/quick-install.md"

Make `bl` available in this terminal and check the installation:

```sh
export PATH="$HOME/.local/bin:$PATH"
bl --version
```

Expect `Branchline CLI v1.0.0-rc.1` (a release candidate). To use `bl` in future
terminals, add that PATH line to your shell profile. See [installation details](install.md)
for supported systems, updates and the optional Node runtime.

Prefer to try it first? Skip installation and continue in the browser below;
you can return to this step before running locally.

## 1. Run an order transformation

[Open Prepare an order payload in the Playground](../playground.md?example=prepare-order).
Select **Run** (or press **Cmd/Ctrl + Enter**) to use this input and program:

### Input

--8<-- "includes/prepare-order/input.md"

### Program

--8<-- "includes/prepare-order/program.md"

### Expected result

--8<-- "includes/prepare-order/expected.md"

The program uses `input` to read the incoming object. `OUTPUT` creates the result:

- `input.id` becomes `order_id`.
- `??` uses `"Not provided"` if the city is missing or `null`.
- The comprehension keeps items where `qty > 0`, then takes their `sku` values.

The example expects a `shipping` object and an `items` array containing objects
with string `sku` and numeric `qty` fields. An empty array produces an empty `skus` array.

## 2. Change the result

In **Input JSON**, change PEN's `qty` from `0` to `1`, then run again.
PEN now passes the filter, so the result is:

--8<-- "includes/prepare-order/exercise-expected.md"

## Run locally

Use **Download program.bl** and **Download input.json** in the Playground to keep
your current edits. Put both files in the same folder and open a terminal in that
folder. Keep the filenames `program.bl` and `input.json`; if your browser adds a
suffix such as `(1)` to a repeat download, rename the new files in that folder.
If you skipped installation, [install Branchline first](#install-branchline).
Then run:

```bash
bl program.bl --input input.json
```

After changing PEN's quantity to `1`, expect:

--8<-- "includes/prepare-order/exercise-expected.md"

If `bl` is not found, run the PATH command above in this terminal. If an input
file is not found, check that you are in the folder containing both downloaded files.

Starting directly in the terminal? Download the original
[program.bl](../downloads/prepare-order/program.bl),
[input.json](../downloads/prepare-order/input.json), and
[expected.json](../downloads/prepare-order/expected.json), then run the same command.
The original input returns only `BOOK`; your edited input returns `BOOK` and `PEN`.
JSON whitespace and object key order may differ between runtimes.

## Continue learning

These three short lessons explain fields, defaults, and arrays. Use the same
Playground editors, or save each program and input to run locally. The commands
below assume the CLI has been installed using the [local setup](install.md).

## Transform a JSON object

### Program

Save this as `hello.bl`:

<!-- onboarding:hello:program -->
```branchline
TRANSFORM Hello {
    LET greet = "Hello, " + input.name
    OUTPUT { greeting: greet }
}
```

### Input example

Save this as `sample.json`:

<!-- onboarding:hello:input -->
```json
{"name":"Ada"}
```

### Expected output

<!-- onboarding:hello:output -->
```json
{"greeting":"Hello, Ada"}
```

### Why this works

`input` holds the input object. `greet` combines a literal prefix with its `name`
field, and `OUTPUT` constructs a new object with a `greeting` field. This example
expects `name` to be a string.

### Validation commands

Using the installed JVM or Node launcher:

```bash
bl hello.bl --input sample.json
```

In the Playground, paste the program and input into their editors, run, and compare
the result with the expected output. JSON whitespace and object key order may differ.

## Handle missing fields and null

### Program

Save this as `defaults.bl`:

<!-- onboarding:defaults:program -->
```branchline
TRANSFORM Defaults {
    OUTPUT {
        name: input.name ?? "Guest",
        nickname: input.nickname,
        active: input.active ?? true,
        attempts: input.attempts ?? 3
    }
}
```

### Input example

<!-- onboarding:defaults:input -->
```json
{"name":null,"nickname":null,"active":false,"attempts":0}
```

### Expected output

<!-- onboarding:defaults:output -->
```json
{"name":"Guest","nickname":null,"active":false,"attempts":0}
```

### Why this works

`??` replaces a missing field or `null` with the value on its right. It preserves
`false`, `0`, and empty strings. Reading `nickname` without a fallback keeps its
`null`; a missing `nickname` also produces `null`. With `{}` as input, the result
is `{"name":"Guest","nickname":null,"active":true,"attempts":3}`.

### Validation commands

Replace `sample.json` with the input above and run:

```bash
bl defaults.bl --input sample.json
```

The same program and input can be pasted into the Playground.

## Filter and project an array

### Program

Save this as `available-items.bl`:

<!-- onboarding:available-items:program -->
```branchline
TRANSFORM AvailableItems {
    LET items = input.items ?? []
    OUTPUT {
        items: [
            { sku: item.sku, total: item.qty * item.price }
            FOR EACH item IN items WHERE item.qty > 0
        ]
    }
}
```

### Input example

<!-- onboarding:available-items:input -->
```json
{"items":[{"sku":"A","qty":0,"price":10},{"sku":"B","qty":2,"price":7},{"sku":"C","qty":1,"price":3}]}
```

### Expected output

<!-- onboarding:available-items:output -->
```json
{"items":[{"sku":"B","total":14},{"sku":"C","total":3}]}
```

### Why this works

`items` defaults to an empty array when the input field is missing or `null`.
The comprehension visits items in order, keeps positive quantities, and constructs
each output row from `sku` and `qty * price`. Here each row has a string `sku` and
small integer `qty` and `price` values. An empty array or no matching rows produces
`{"items":[]}`. See [Numeric Semantics](../language/numeric.md) before using large
integers or decimal amounts.

### Validation commands

Replace `sample.json` with the input above and run:

```bash
bl available-items.bl --input sample.json
```

The same program and input can be pasted into the Playground.

These three programs, inputs, and outputs are read directly from this guide by the
conformance tests on JVM and Node. Both interpreter execution and strict VM
execution (with interpreter fallback disabled) are checked, along with missing
fields, explicit nulls, and empty or fully filtered arrays. This is a bounded set
of examples; see the [release readiness scope](release-readiness.md) for wider support.

## Next steps

- Learn the syntax in the [Tour of Branchline](../learn/index.md).
- Use [TRY/CATCH](try-catch.md) to keep pipelines resilient.
- Explore the [Standard Library](../language/std-core.md).
