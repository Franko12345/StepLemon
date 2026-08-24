# ADR 0005: Use Mockito-core for JVM unit tests

- **Status:** Accepted
- **Date:** 2026-08-24
- **Decider:** Franco Valois Delucca
- **Supersedes:** part of `agents.toml` invariants.must_not ("No new deps without an ADR")

## Context

`agents.toml` states no new third-party dependencies without an ADR. Ticket
06 (test scaffolding for the Zepp parser) needs to instantiate
`StepRepository`, which requires a non-null `Context` to access
`SensorManager`. `Context` is part Android type and cannot be implemented
directly in JVM tests.

## Considered options

1. **Mockito-core** — 1 dep, ~2 MB, standard in 95% of Android projects.
   Lets `mock(Context::class.java, RETURNS_DEFAULTS)` return a usable
   stub in one line.
2. **Robolectric** — 1 dep + ~80 MB of test runtime. Overkill for one
   parser test.
3. **Hand-rolled FakeContext extending android.content.Context** — not
   possible; `Context` is abstract on Android, and the JVM stub
   `android.jar` provided by the Android Gradle Plugin throws
   `RuntimeException("Stub!")` on every method call.
4. **Refactor StepRepository to take `Context?` and lazy-init sensor
   fields** — keeps the production API unchanged in practice but
   propagates nullability through the codebase and bloats the diff.

## Decision

**Add `org.mockito:mockito-core:5.12.0` as `testImplementation`.**

The dep is test-only (zero APK impact), stable, and well-known. The
alternative (option 4) is more invasive than warranted by the single
parser test.

## Consequences

**Positive**
- Tests run as plain JVM (no Robolectric overhead).
- One-line Context stub.
- Easy to extend tests later (any Android type can be mocked).

**Negative**
- Mockito-core ~2 MB on the developer's machine and in CI cache.
- Adds a third-party test framework that needs updating occasionally.

**When this ADR should be revisited**
- If a future test needs more Android-specific mocking than Mockito
  provides (consider Robolectric then).
- If the StepRepository test grows past ~5 cases (extract
  `parseDailyCursor` to a top-level function so it's testable without any
  Context).