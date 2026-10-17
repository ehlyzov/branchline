# Experimental VM fusion plan

This slice implements the fusion preparation used by the opt-in slots/fusion
factorial experiment in `planning/vm-slots-fusion-experiment.md`. The parent
agent owns integration records, correctness runs, and performance measurements.

The prepared plan indexes the original program counters and leaves canonical
bytecode, opcode numbering, constants, and serialization unchanged. Two patterns
are eligible: a variable/local/scope load followed by static path accesses, and
`DUP; STORE_VAR; STORE_LOCAL` mirrored stores. Neither pattern specializes value
types. All jump, loop, and exception-handler destinations remain valid entry
points; a pattern never consumes one in its interior.

Execution must retain the original stack operations, failure order, partial
effects, and source program counter at each helper. The runtime owns observer
fallback, including tracing, stepping, custom environments/containers, calls,
closures, and suspension. A failure must propagate without replaying effects.
Slots-only execution must not silently use the fusion treatment.

Preparation tests cover the patterns, entry boundaries, explicit observer
instructions, source immutability, and inert constants. Builds and benchmarks
are reserved for the parent so CPU-heavy runs remain sequential.
