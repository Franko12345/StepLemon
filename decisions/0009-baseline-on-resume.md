# ADR 0009: Midnight baseline captured on resume (the "passos antes de abrir" fix)

- **Status:** Accepted
- **Date:** 2026-09-04
- **Decider:** Hermes (design), with spec sign-off from Franco
- **Depends on:** ADR 0001, ADR 0006

## Context

After ticket 11's 4-layer fix landed (PR #6, v3.3), the user reported a
second-order problem: the step counter displays **0 steps** the first
time the app is opened each day, even though they walked before opening
the app. The Today screen only updates to the correct count after the
first `TYPE_STEP_COUNTER` event of the day fires.

### Why the 4-layer fix did not catch this

ADR 0006 fixed the merge path so that today's row in
`readMergedHistory()` would include the native sensor value when Zepp
returned `0`. But:

- Zepp's `parseDailyCursor()` fills missing dates with `0` (not `null`),
  so the merge's `nativeToday` fallback only fires when both inputs
  are 0 OR Zepp explicitly returned 0 for today. Either way, if the
  sensor hasn't yet fired today, `nativeToday = 0` and the display is
  `0`.
- The sensor only fires on **physical movement**. Sitting at a desk,
  in a meeting, or driving a car, the raw step counter never
  increments. So the baseline (`midnightRawTotal`) is only captured on
  `onSensorChanged()` — which means it is captured AT the first step
  of the day, **after** the user opens the app. The delta
  `lastRawTotal - midnightRawTotal` over-counts whatever happened
  between midnight and the first step.

### Constraint (carried over from ticket 11)

The opt-in midnight rollover (ADR 0008) is the only "background-side"
solution. It requires the user to flip a toggle, a notification-free
alarm at 23:55, and grants the app the ability to persist one number
per day. **This ticket does not change that.** It is the
**default-on, zero-permission, no-opt-in** safety net for the
"opened the app early in the day" case.

## Decision

Introduce `refreshMidnightBaseline()`. Extracted from the inline
`rollMidnightIfNeeded()` logic (which already runs inside
`onSensorChanged()`), but now **also called from
`readNativeStepsToday()`** on every read.

Behavior:

```kotlin
private fun refreshMidnightBaseline() {
    val today = todayDate()
    if (midnightDate == today || lastRawTotal < 0L) return
    midnightRawTotal = lastRawTotal
    midnightDate = today
    midnightCapturedAt = System.currentTimeMillis()
    prefs.edit()
        .putLong(KEY_MIDNIGHT_RAW, lastRawTotal)
        .putString(KEY_MIDNIGHT_DATE, today)
        .apply()
}
```

The capture is **gated by `lastRawTotal >= 0L`** so we never persist
a stale or zero baseline. `TYPE_STEP_COUNTER` always fires at boot on
Android, so for the overwhelming majority of users this triggers
immediately on app open.

### Where the call goes

`readNativeStepsToday()` — the read path. One helper, every caller
benefits:

- `TodayFragment.refresh()` (the 2-second loop in the Today tab).
- `StatsFragment.refresh()` (via `readMergedHistory()` → which itself
  calls `readNativeStepsToday()`).
- `HistoryFragment.refresh()` (same path).

We do NOT add a separate hook to every fragment's `onResume` — the
read path is the right place because the read is what matters.

### UI addition (today screen, lemon theme, minimal)

- Pill (`source_pill`) becomes informative: when the source is the
  native sensor AND the baseline was captured in this session, show
  `📡 Sensor (desde HH:MM)`. This makes the "today started at open"
  provenance visible to the user.
- A subtle lemon-tinted caption below the steps value (`caption_pre_open`)
  is shown only when (a) the active source is the sensor AND (b) the
  baseline was captured in this process lifetime. Text:
  *"Passos pré-abertura contabilizados"*.

No new layouts, no new colors, no animations.

## Why this is acceptable

- **Zero new permissions.** Uses `TYPE_STEP_COUNTER` and
  `SharedPreferences` we already read.
- **Zero new background work.** No service, no alarm, no notification.
  Consistent with ADR 0002 (no foreground service).
- **Compatible with the opt-in rollover (ADR 0008).** ADR 0008
  persists a history row at 23:55 — which is what protects users who
  never open the app during a day. This ADR protects the user who
  DOES open the app but stayed still for hours after. They are
  complementary, not redundant.
- **Backward compatible.** Existing prefs (`KEY_MIDNIGHT_RAW`,
  `KEY_MIDNIGHT_DATE`) are reused. No migration.
- **No Elvis reintroduction.** The merge path (ADR 0006 invariant) is
  untouched.

## Behavior matrix

| State on open | Before | After |
|---|---|---|
| App opened yesterday, sensor fired, app reopened today morning (no movement yet) | Wrong: shows ~0 or stale delta | Baseline = lastRawTotal at this moment; subsequent steps counted from there. Pill shows `Sensor (desde HH:MM)` |
| App opened today for first time, has not yet walked, lastRawTotal == -1 | 0 | 0 (no lastRawTotal; silently correct — gated by the `< 0` check) |
| Zepp-authorized, Zepp returned nonzero for today | Zepp value | Zepp value (unchanged — this path is not affected) |
| Zepp-authorized, Zepp returned 0 for today, sensor fired earlier | 0 until first step of the day | Sensor value immediately. Pill still says `🍋 Zepp` because `hasZepp` is true — the merge uses native but the pill is the trust label, not the data source |

## Out of scope (deliberately)

- "Show steps even before the sensor has fired." Impossible without
  Zepp or pre-existing local history. ADR 0008's opt-in rollover is
  the answer. We do NOT bump it to default (would be a notification
  ask).
- Replacing the Zepp-vs-sensor merge path. ADR 0001 + the 4-layer fix
  (ADR 0006) already lock the policy.

## Consequences

**Positive**
- The 90% case the user reported is fixed with one helper and two
  tests. No UX redesign.
- The pre-open caption makes the fix *visible* — the user can tell
  their count started at app-open, not at the first step.
- The pill now distinguishes a Zepp-authorized session from a sensor
  session from a sensor-with-pre-open-capture session.

**Negative**
- Adding `midnightCapturedAt` is a new `@Volatile` field on the
  repo. One extra Long per process. Acceptable.
- The pill and caption have to be coordinated — three booleans
  (`hasZepp`, `nativeSteps != null`, `midnightCapturedAt != null`)
  could be a state machine instead. Kept as a boolean `when` for now
  (4 cases, not 4ⁿ); revisit if it grows.

**When this ADR should be revisited**
- If a fourth "false 0 steps" symptom emerges — e.g. Zepp returned
  a real number but it's a stale week-old value — add a `readZeppDate`
  check.
- If Health Connect is added as a third source — re-export the
  helper for that path; the auto-cure is per-source.
