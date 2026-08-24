# Ticket 06 — Finalize Zepp parser with real schema

- **Status:** 📋 Ready for agent
- **Spec ref:** [ADR 0001](../decisions/0001-zepp-vs-sensor.md)
- **Owner:** TBD
- **Depends on:** 05 (needs the real-schema dump)
- **Blocks:** nothing

## Goal

Replace the best-effort `StepRepository.parseDailyCursor()` with a parser
that handles the actual Zepp `day_total_summary` schema exposed on HyperOS 1.0.10.

## Acceptance criteria

- [ ] Update `parseDailyCursor()` to read the column names returned by the dump.
- [ ] Update `normalizeDate()` to handle the date format returned by the dump (`yyyy-MM-dd`, `yyyyMMdd`, `epoch-millis`, etc.).
- [ ] If the schema exposes a `user_id` filter, add a projection or selection so the parser doesn't return data for other accounts on the same device.
- [ ] Update the "all zeros" sentinel logic — if the actual authorized response has zero rows (not zeros in cells), distinguish from unauthorized.
- [ ] Add a unit test using a fake `ContentResolver` that returns a representative cursor (extracted from the dump).
- [ ] No new third-party dependencies.
- [ ] APK still <10 MB; gradle build still exits 0.

## Files

- `app/src/main/java/com/stepwatch/app/StepRepository.kt`
- `app/src/test/java/com/stepwatch/app/StepRepositoryTest.kt` (new)

## Smoke

After implementing, on the target Poco M5 with Zepp installed:

1. Open StepLemon.
2. Stats tab should show a 7-bar weekly chart with non-zero values for days Zepp tracked.
3. History tab should list days with correct step counts.
4. Today tab should match Zepp's own count.

## Notes

- Use the dump the user shared via ticket 05 as ground truth. If the dump is incomplete (e.g. Zepp not authorized on the dev device), mark this ticket blocked and surface back to the user.