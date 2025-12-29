---
title: Production Use
---

# Production Use

This guide focuses on operating Branchline in real workloads: predictable
behavior, resilient error handling, and clean host integration.

## Best practices
- **Normalize inputs early.** Convert ambiguous data (e.g., missing fields,
  mixed number/string types) to a stable shape before doing complex logic.
- **Keep transforms small.** Prefer multiple focused transforms and helper
  functions over a single mega-transform.
- **Prefer explicit defaults.** Use `??` and `CASE` to make fallback paths
  obvious to readers and maintainers.
- **Use types as contracts.** Define `TYPE` aliases and keep them close to
  transforms that use them to communicate expectations.
- **Trace with intent.** Use `CHECKPOINT` and `EXPLAIN` to mark critical
  milestones rather than every operation.

## Error-handling recipes

### Safely wrap external calls
```branchline
LET response = TRY CALL inventoryService(input) -> payload
CATCH(err) RETRY 2 TIMES BACKOFF 250ms -> {
    ok: false,
    error: err.message ?? "inventory lookup failed"
};
```

### Guard outputs with assertions
```branchline
LET total = SUM(input.items);
ASSERT(total >= 0, "total cannot be negative");
OUTPUT { total: total };
```

### Convert failures into soft warnings
```branchline
LET flagged = TRY ASSERT(input.ready, "missing readiness flag")
CATCH(err) -> false;

OUTPUT {
    ready: flagged,
    warnings: flagged ? [] : ["Input was not ready"]
};
```

## Host integration patterns

### Adapter-driven IO
Use `OUTPUT` adapter specs to route data to external systems, keeping the
transformation logic separate from transport.

### Callable host services
`CALL` integrates with host-provided functions. Follow these patterns:
- Validate arguments before `CALL`.
- Wrap `CALL` in `TRY`/`CATCH` if failures should not abort the pipeline.
- Translate host errors into a stable, user-friendly shape.

### Shared memory
Declare shared resources with `SHARED` and keep mutations localized:
```branchline
SHARED session MANY;

session["lastSeen"] = NOW();
```
If you need to wait for shared state, use `AWAIT_SHARED`, and ensure the host
runtime is configured with a shared store.

### Asynchronous control
`AWAIT` and `SUSPEND` require host support. Use them to model long-running
tasks, and pair them with clear status outputs so downstream systems can
resume safely.

## Operational checklist
- Capture inputs and outputs at boundaries for debugging.
- Store compiled bytecode for repeatable execution across hosts.
- Monitor retry counts and backoff delays to prevent runaway workloads.
- Keep a migration guide per release once compatibility is introduced.
