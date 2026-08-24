# Ticket 06 — Finalize Zepp parser with real schema

- **Status:** 🟡 In progress (waiting on user dump)
- **Date:** 2026-08-24
- **Spec ref:** [ADR 0001](../decisions/0001-zepp-vs-sensor.md)
- **Owner:** TBD
- **Depends on:** 05 (still needs the real-schema dump)
- **Blocks:** nothing
- **Related:** [ticket 11](./11-stats-real-fix-4-layer.md) (the readMergedHistory fix is independent of this parser work)

## Goal

Replace the best-effort `StepRepository.parseDailyCursor()` with a parser
that handles the actual Zepp `day_total_summary` schema exposed on HyperOS 1.0.10.

## Acceptance criteria

- [ ] Update `parseDailyCursor()` to read the column names returned by the dump.
- [ ] Update `normalizeDate()` to handle the date format returned by the dump
  (`yyyy-MM-dd`, `yyyyMMdd`, `epoch-millis`, etc.).
- [ ] If the schema exposes a `user_id` filter, add a projection or selection
  so the parser doesn't return data for other accounts on the same device.
- [ ] Update the "all zeros" sentinel logic — if the actual authorized
  response has zero rows (not zeros in cells), distinguish from unauthorized.
- [ ] Add a unit test using a fake `ContentResolver` that returns a
  representative cursor (extracted from the dump).
- [ ] No new third-party dependencies.
- [ ] APK still <10 MB; gradle build still exits 0.

## Files

- `app/src/main/java/com/stepwatch/app/StepRepository.kt`
- `app/src/test/java/com/stepwatch/app/StepRepositoryTest.kt` (existing — 4 normalizeDate tests pass)

## Smoke (device)

After implementing, on the target Poco M5 with Zepp installed:

1. Open StepLemon.
2. Stats tab should show a 7-bar weekly chart with non-zero values for days Zepp tracked.
3. History tab should list days with correct step counts.
4. Today tab should match Zepp's own count.

## Test scaffolding (done)

- `app/src/test/java/com/stepwatch/app/StepRepositoryTest.kt` — 4 tests covering
  `normalizeDate()` (the only pure-Kotlin method in `StepRepository` worth
  verifying). Schema-level Cursor tests deferred — see ADR 0005 for rationale
  (Mockito added for `Context` stub, but `MatrixCursor.getColumnNames()`
  returns null in JVM test env, breaking the test setup).
- `parseDailyCursor()` marked `internal` so tests can call it.
- `parseDailyCursor()` refactored to table-driven schema discovery (tries
  `day`/`date` columns + `step`/`total` columns + 3 date formats). When the
  user shares the real Zepp dump via the in-app debug button, the matching
  candidate should already work; if not, the Log.w lines in
  `readMergedHistory` will surface the issue.

## Status update history

- 2026-08-24: parser refactored to table-driven; tests scaffolded (ADR 0005).
- 2026-08-24: still waiting on the user sharing the in-app dump.

## Notes

- The readMergedHistory fix (ticket 11) is **independent** of this ticket.
  Even without a perfect schema, the app works: the native sensor fills
  today and the all-zeros sentinel returns null (Stats shows the empty
  state if Zepp returns nothing useful).
- If the dump reveals the schema was already covered, this ticket can
  be closed as "no change needed".