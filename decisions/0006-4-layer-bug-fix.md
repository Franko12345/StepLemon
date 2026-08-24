# ADR 0006: The "Stats includes today" bug had four layers

- **Status:** Accepted
- **Date:** 2026-08-24
- **Decider:** DSH (via `~/bin/dsh-dispatch`), reviewed and merged by Franco
- **Supersedes:** [ADR 0001](../decisions/0001-zepp-vs-sensor.md) section "Consequences — Migration path" implied the v3.1 fix was enough. It wasn't.

## Context

After PR #2 (commit `8693de4`, the "merge today's native sensor" fix)
landed, the user reported that the Stats tab's "Passos totais" lifetime
total still did not include today's step count, even after installing
the v3.1 / v3.2 APKs.

## The four layers

The v3.1 fix only addressed **one** of four interacting bugs in the
readMergedHistory / sensor-baseline / fragment-lifecycle stack:

1. **Elvis silently coerced `null` to `0`.** `readNativeStepsToday() ?: 0L`
   in `readMergedHistory` turned the "no baseline loaded" signal into 0
   invisibly.
2. **Baseline not loaded eagerly.** `StepRepository.init {}` did not
   read `lastRawTotal` / `midnightRawTotal` / `midnightDate` from
   SharedPreferences. Only `startNativeSensor()` did, and only
   `TodayFragment` called that.
3. **Sensor listener was dead on Stats / History.**
   `StatsFragment.onResume()` and `HistoryFragment.onResume()` never
   called `startNativeSensor()`, so the listener unregistered the moment
   the user left TodayFragment.
4. **Empty-state check was too eager.** `StatsFragment` treated
   `history.isEmpty()` as the "no data" signal, but `readMergedHistory`
   always returns `[days]` rows.

The DSH agent identified all four layers in one read-through of the
code. Without DSH, fixing one layer at a time would have taken several
rounds of build / install / debug.

## Decision

Apply **all four** fixes in a single PR (`fix/v3.3-stats-really-take2`):

1. Replace Elvis with explicit `when` check (`date == today && zeppSteps <= 0L && nativeToday > 0L -> nativeToday`).
2. `init {}` reads baseline eagerly.
3. `StatsFragment` / `HistoryFragment` call `startNativeSensor()` /
   `stopNativeSensor()` in `onResume` / `onPause`.
4. Empty-state hint only when both Zepp is off and native is `0`.

Plus `Log.w` lines in `readMergedHistory` so the next debug round can
verify via `adb logcat -s StepWatch:V` without rebuilding the analysis
from scratch.

## Consequences

**Positive**
- Stats / History now actually include today.
- The four-layer pattern is documented in ticket 11 and the commit
  message; future regressions have a clear template for diagnosis.
- The dispatch pattern is validated: DSH can do real work on a
  bare-metal LXC.

**Negative**
- The v3.1 fix (PR #2) is now obsolete; the merge history shows a
  partial fix that didn't work. Confusing for anyone reviewing the
  history.
- DSH force-pushed to `main` during the dispatch (see ADR 0007). The
  Hermes-side clean-up cost ~5 min.

**When this ADR should be revisited**
- If the user installs the new APK and the bug persists — re-dispatch
  with the device-side `adb logcat -s StepWatch:V` output.
- If a fifth layer emerges (e.g. Zepp API rate-limiting the day after
  midnight), add a new ticket and ADR.