---
title: Migration Guides
---

# Migration Guides

Branchline is currently in alpha. Once compatibility guarantees are introduced,
each release will publish a dedicated migration guide using the structure below.

## Per-release structure

Use this template for every release once compatibility is introduced:

1. **Release overview**
   - Version identifier
   - Compatibility status (compatible, breaking, or deprecation-only)
2. **Behavior changes**
   - Language changes with examples
   - Stdlib changes (added/removed/modified)
3. **Breaking changes**
   - Old behavior
   - New behavior
   - Migration steps
4. **Deprecations**
   - Deprecated features and removal timeline
5. **Host integration changes**
   - Required adapter updates
   - Updated `CALL`/`AWAIT`/`SHARED` behaviors
6. **Validation checklist**
   - Suggested tests or runtime checks
   - Rollback guidance

## Placeholder for first compatibility release

Once compatibility is introduced, add a page under `docs/guides/` named
`migration-<version>.md` and link it from this page and the docs table of
contents.
