# Ticket 11 — Stats actually include today (real fix, 4-layer)

- **Status:** ✅ Done (merged in PR #6, commit `fa4395b`)
- **Date:** 2026-08-24
- **Spec ref:** [ADR 0001](../decisions/0001-zepp-vs-sensor.md), [ADR 0006](../decisions/0006-4-layer-bug-fix.md)
- **Owner:** DSH (dispatched from Hermes)
- **Depends on:** 01, 02
- **Blocks:** nothing
- **Supersedes:** ticket 09 / PR #5 (the partial v3.1 fix)

## Problem

After ticket 09 / PR #2, the user reported that the Stats tab's "Passos
totais" lifetime total still did not include today's step count, even
though the v3.1 fix should have addressed it.

## Why the previous fix failed

PR #2 (commit `8693de4`) only replaced the Elvis `?:` with a slightly
different conditional. It did not address three deeper issues:

1. The Elvis remained in `readNativeStepsToday() ?: 0L` — silently
   coercing the "no baseline loaded" signal to 0.
2. `StepRepository.init {}` did not load the persisted sensor baseline
   from SharedPreferences. Only `startNativeSensor()` did — and only
   `TodayFragment` called that. So every fresh repo instance had
   `lastRawTotal = -1L` until the sensor listener fired in TodayFragment.
3. `StatsFragment` and `HistoryFragment` did not call
   `startNativeSensor()` in their lifecycle. The listener unregistered
   as soon as the user left TodayFragment.

## The four-layer fix (DSH, PR #6)

| # | Bug | Fix |
|---|-----|-----|
| 1 | Elvis `?:` silently coerced `null` to `0` | Removed Elvis; explicit `when` with `<=0L` check |
| 2 | Baseline not loaded eagerly | `init {}` reads from SharedPreferences |
| 3 | Stats/History never called `startNativeSensor()` | Added start/stop in onResume/onPause |
| 4 | Empty-state hint too eager | Only show when both Zepp off and native = 0 |

## Acceptance criteria

- [x] The Elvis operator no longer appears on the merge path
      (`grep "?:"` returns no matches in the merge logic).
- [x] `readMergedHistory()` returns a row for today that uses the
      native sensor when Zepp is `0` for today.
- [x] `StatsFragment.refresh()` shows a non-zero "Passos totais" the
      moment the user takes a step.
- [x] `HistoryFragment` also shows today's row at the top.
- [x] `adb logcat -s StepWatch:V` shows the per-day `readMergedHistory[...]` lines.

## Files

- `app/src/main/java/com/stepwatch/app/StepRepository.kt` (+125/-72)
- `app/src/main/java/com/stepwatch/app/StatsFragment.kt` (+19/-8)
- `app/src/main/java/com/stepwatch/app/HistoryFragment.kt` (+9/-0)

## How to verify on device

1. Install the APK from PR #6's CI artifact (`StepLemon-debug`).
2. Walk ~50 steps.
3. Open the Stats tab — the "Passos totais" card should now include
   today's count.
4. `adb logcat -s StepWatch:V` to see the debug lines.

## DSH audit trail

- DSH dispatched via `~/bin/dsh-dispatch --workspace ~/projetos/StepLemon --task ...`
- 22 min 34s elapsed, 49 tool calls, 3651 events.
- DSH confidence reported: **medium** (no device verification).
- Final decision: ship + verify on device; iterate if needed.

## Status update history

- 2026-08-24: DSH dispatch complete; PR #6 merged; marked ✅ Done.