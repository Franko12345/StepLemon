# Ticket 02 — Zepp Life ContentProvider integration

- **Status:** ✅ Done (best-effort)
- **Date:** 2026-08-24
- **Spec ref:** [ADR 0001](../decisions/0001-zepp-vs-sensor.md), [spec.md → Technical choices](../spec.md#technical-choices)
- **Owner:** Franco
- **Depends on:** 01
- **Blocked by:** 05 (real-schema dump) for full correctness

## Goal

Read today's and historical daily steps from Zepp Life's ContentProvider when
installed and authorized.

## Acceptance criteria

- [x] `StepRepository.isZeppInstalled()` returns true when `com.xiaomi.hm.health` is on the device.
- [x] `StepRepository.isZeppAuthorized()` returns true when the provider responds without `SecurityException`.
- [x] `StepRepository.readZeppStepsToday()` returns `Long` from `step_counter` provider when available, else null.
- [x] `StepRepository.readZeppHistory(days)` queries `day_total_summary` and returns a list of `DailySteps` for the requested window (today first).
- [x] `parseDailyCursor()` handles column names `day`/`date` and `step`/`total` with date normalization (`yyyyMMdd` → `yyyy-MM-dd`).
- [x] "All zeros" sentinel detected and treated as unauthorized (returns null).
- [x] Permissions `com.xiaomi.hm.health.READ` / `WRITE` declared in manifest (the user authorizes via Zepp's UI).

## Files

- `app/src/main/java/com/stepwatch/app/StepRepository.kt` (`isZeppInstalled`, `isZeppAuthorized`, `readZeppStepsToday`, `readZeppHistory`, `parseDailyCursor`, `normalizeDate`)
- `app/src/main/AndroidManifest.xml` (Zepp permissions)

## Smoke

Cannot smoke without a device that has Zepp installed and authorized. The
unit-testable surface is the schema parser (see ticket 06).

## Known gaps

- The actual column names and date format on HyperOS 1.0.10 are **not verified**.
  Best-effort guessing from MIUI 12 reports. Ticket 05 will collect real data;
  ticket 06 will finalize the parser.