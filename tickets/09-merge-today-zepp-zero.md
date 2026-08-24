# Ticket 09 — Merge today's Zepp-zero with native sensor

- **Status:** 🟡 In progress (branch `fix/merge-today-zepp-zero`)
- **Date:** 2026-08-24
- **Spec ref:** [ADR 0001](../decisions/0001-zepp-vs-sensor.md), [spec.md → Technical choices](../spec.md#technical-choices)
- **Owner:** Hermes
- **Depends on:** 01, 02
- **Blocks:** nothing

## Problem

`StepRepository.readMergedHistory()` introduced in v1.2 still left today's
step count out of the lifetime totals.

### Root cause

`readZeppHistory()` always populates today's row (filling `0L` when Zepp
hasn't yet consolidated today's total — Zepp writes daily totals at
midnight, not in real time). The merge logic used:

```kotlin
val steps = zepp[date] ?: when (date) {
    today -> nativeToday
    else -> 0L
}
```

The `?:` Elvis only fires when `zepp[date]` is **null**, but Zepp returns a
row with value `0` for today — so the Elvis never fired, and today's
sensor-native value was always discarded.

### Fix

When today's Zepp row is `0` (or absent) AND the native sensor has a
positive value, use the native sensor. Zepp's value still wins when it
is positive (i.e. for past days, or once Zepp has consolidated today).

```kotlin
val zeppSteps = zepp[date] ?: 0L
val steps: Long = when {
    date == today && zeppSteps <= 0L && nativeToday > 0L -> nativeToday
    else -> zeppSteps
}
```

## Acceptance criteria

- [x] When Zepp returns today's row as `0` and the native sensor reports
      a positive value, the merged history uses the native value.
- [x] When Zepp returns a positive value for today (after midnight rollover
      has written it), the Zepp value is used (native sensor is ignored).
- [x] Past days are unaffected — still come from Zepp directly.
- [ ] APK rebuilt and tested by the user (currently in this branch).

## Files

- `app/src/main/java/com/stepwatch/app/StepRepository.kt`

## Smoke

1. Install the rebuilt APK on the Poco M5.
2. Take a walk; observe the "Today" tab counting up.
3. Switch to "Stats" — the "Passos totais" card should now include today's
   progress (visible as soon as the user takes the first step).
4. Compare to Zepp's own count: should match within ±1 step (Zepp may
   consolidate slightly differently).

## Notes

- If the native sensor is `0` (just-installed, hasn't received events yet)
  AND Zepp is `0`, today shows as `0` — correct.
- If Zepp's `day_total_summary` returns `null` entirely (provider
  unauthorized), the merged history only has native-today — still better
  than v1.1 (everything blank).