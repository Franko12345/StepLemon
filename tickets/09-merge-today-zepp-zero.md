# Ticket 09 — Merge today's Zepp-zero with native sensor

- **Status:** ✅ Done (superseded by v3.3 take-2)
- **Date:** 2026-08-24
- **Spec ref:** [ADR 0001](../decisions/0001-zepp-vs-sensor.md)
- **Owner:** Hermes → DSH
- **Depends on:** 01, 02
- **Blocks:** nothing

## Outcome

Closed by **PR #6** (commit `fa4395b`, DSH-authored, branch
`fix/v3.3-stats-really-take2` — squashed into `main`). DSH identified that
the v3.1 fix (PR #2) was only a partial fix; the real bug had **four
layers** (see ADR 0006 / commit message).

## Final state of the merge logic

`StepRepository.readMergedHistory()` now:

1. Loads the persisted `lastRawTotal` / `midnightRawTotal` baseline eagerly
   in `init {}` (so any repo instance has a usable value).
2. Uses an explicit `when` check (no Elvis) to fall back to the native
   sensor when today's Zepp value is `0`.
3. Has no Elvis operator remaining on the merge path.
4. Logs each day's resolution to `Log.w(TAG, "readMergedHistory[...]")`
   so the next debug round can verify via `adb logcat -s StepWatch:V`.

## Layer-by-layer

| Layer | Bug | Fix |
|-------|-----|-----|
| 1 | Elvis `?:` silently coerced `null` (no baseline) to `0` | Removed Elvis, explicit `when` |
| 2 | Baseline not loaded eagerly | `init {}` reads from SharedPreferences |
| 3 | Stats/History never called `startNativeSensor()` | Added start/stop in onResume/onPause |
| 4 | Empty-state hint too eager | Only show when both Zepp off and native = 0 |

## Status update history

- 2026-08-24 (PR #2): v3.1 partial fix — Elvis only. Did not resolve the bug.
- 2026-08-24 (PR #6): v3.3 take-2 — full 4-layer fix via DSH. Marked ✅ Done.