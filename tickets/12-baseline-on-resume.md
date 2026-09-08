# Ticket 12 — Baseline da meia-noite capturado proativamente ao abrir o app

- **Status:** 📋 Ready for agent
- **Date:** 2026-09-04
- **Spec ref:** [`spec.md`](../spec.md) § Goal, [ADR 0001](../decisions/0001-zepp-vs-sensor.md), [ADR 0006](../decisions/0006-4-layer-bug-fix.md), [ADR 0008](../decisions/0008-opt-in-midnight-rollover.md)
- **Owner:** Hermes (TDD), DSH review
- **Depends on:** 11 (4-layer fix), 08 (no foreground service)
- **Blocks:** nothing
- **Supersedes:** nothing — this is a complementary safety net, not a replacement for the opt-in rollover

## Problem

The user reports that the step counter starts from 0 the first time the app is
opened in a day, even though they have walked steps before opening it.

### Two root causes (already diagnosed)

A) **Sensor race.** `midnightRawTotal` is only captured inside
`onSensorChanged()` (or `resetToday()`). If the user opens the app at 7h
but does not move until 14h, no `onSensorChanged` fires, so the baseline
captured in prefs is still **yesterday's** value (or missing for first
launch). `lastRawTotal - midnightRawTotal` then yields either 0 or a
nonsensical "since yesterday" delta.

B) **Zepp + sensor merge is too eager toward Zepp-zero.** In
`readMergedHistory`, today's row uses `nativeToday` only when
`zeppStepsValue <= 0L && nativeTodayValue > 0L`. During the morning of a
day Zepp hasn't yet consolidated (Zepp writes at 23:59/00:00), both
inputs are 0 and the user sees 0.

### Constraints (do NOT violate)

- **No foreground service** (spec Non-goals; ADR 0002). MIUI/HyperOS
  aggressively kill these and we'd need a persistent notification.
- **No new persistent background work.** The opt-in midnight alarm (ADR
  0008) is the only opt-in boundary. This ticket does NOT touch it.
- **No new permissions.** Solution must use only `TYPE_STEP_COUNTER`
  and SharedPreferences we already read.
- **No UI redesign beyond the agreed pill + pre-open badge.** Out of
  scope is redesigning Today; this is a bug fix + minimal UX addition.

## Decision (auto-cure, passive)

On any `readNativeStepsToday()` invocation, before computing the delta,
check whether `midnightDate` in prefs is **stale** (not equal to today).
If it is AND `lastRawTotal >= 0L` (sensor has fired at least once since
boot), capture the baseline **now**:

```
midnightRawTotal = lastRawTotal
midnightDate = today
prefs.edit().putLong(MIDNIGHT_RAW, lastRawTotal).putString(MIDNIGHT_DATE, today).apply()
```

This is the same logic `rollMidnightIfNeeded()` already runs inside
`onSensorChanged` — we are simply decoupling it from the sensor event.

The capture is gated by `lastRawTotal >= 0` so we never pollute prefs
with a stale boot value (e.g., right after a reboot before the sensor
fires). This is **free**: `TYPE_STEP_COUNTER` always fires at boot on
Android, but if we somehow haven't seen one event, we don't pretend we
have.

### Where the call goes

- `readNativeStepsToday()` (the read path) calls it BEFORE the delta
  calculation. This makes every consumer correct, not just TodayFragment.
- `TodayFragment.onResume` and `SettingsFragment.onResume` do NOT need
  to call it explicitly — they already call `readNativeStepsToday` via
  the refresh loop. But `StatsFragment`/`HistoryFragment` use
  `readMergedHistory` which calls `readNativeStepsToday()` — covered.

### Behavior matrix

| State on open | Before fix | After fix |
|---|---|---|
| App opened yesterday, walked, slept, opened today (sensor already fired in background OR foreground) | Wrong: shows 0 or yesterday's delta | Correct: baseline = `lastRawTotal` at this moment; delta from now is right |
| App opened today for first time, has not yet walked | 0 | 0 (no lastRawTotal; silently correct) |
| App opened today, walked 50 steps yesterday at 23h, sleeps at 23:50, opens today at 06h | Wrong: shows ~50 (steps since 23h, not since 00h) | Correct: baseline = lastRawTotal at 06h; subsequent steps counted from there |
| Zepp-authorized user, Zepp returned 0 for today | 0 (and native = 0 because no sensor yet either) until first step | Same — the merge path needs `nativeTodayValue > 0L` to fall back. We do NOT relax this. The user sees 0 until they take one step. This is consistent with the "must take a first step" reality; we cannot introspect a sensor that hasn't fired. (Documented below as **Out of scope**.) |

### Out of scope (deliberately)

- "Show steps even before the sensor has fired." Impossible without
  Zepp or pre-existing local history. ADR 0008's opt-in rollover is the
  solution for that path — and we do NOT bump it to default (would be
  a permission/notification ask).
- Replacing the Zepp-vs-sensor merge path. ADR 0001 + the 4-layer fix
  (ADR 0006) already lock the policy. Touch only the missing morning-
  open case.

## Acceptance criteria

- [ ] `grep "refreshMidnightBaseline" app/src/main/java/com/stepwatch/app/StepRepository.kt`
      shows a single new private fun called from `readNativeStepsToday()`.
- [ ] `grep "?: 0L" app/src/main/java/com/stepwatch/app/StepRepository.kt`
      still has zero matches on the merge/read path (ADR 0006 invariant).
- [ ] `StepRepositoryTest.kt` has a new JVM test:
      `baseline_capture_when_midnight_date_is_stale_writes_prefs`,
      using Mockito on prefs editor and asserting:
      (a) it writes `KEY_MIDNIGHT_RAW` with `lastRawTotal`, and
      (b) it writes `KEY_MIDNIGHT_DATE` with today's `yyyy-MM-dd`.
- [ ] `StepRepositoryTest.kt` has a new JVM test:
      `baseline_capture_skipped_when_last_raw_unset` (lastRawTotal = -1
      → no editor writes).
- [ ] `./gradlew assembleDebug --no-daemon --console=plain` succeeds.
- [ ] `./gradlew testDebugUnitTest --no-daemon --console=plain` passes.
- [ ] `$ANDROID_HOME/build-tools/34.0.0/apksigner verify
      app/build/outputs/apk/debug/app-debug.apk` exits 0.
- [ ] APK size stays under 10 MB.
- [ ] `adb logcat -s StepWatch:V` after install + open shows the new
      `readNativeStepsToday` log line on first read with the captured
      values.

## UX addition (lemon theme, minimal)

`TodayFragment.refresh()` already computes `steps` from
`zeppSteps ?: nativeSteps ?: 0L`. Make two additions:

1. **`source_pill` becomes informative.** Replace current text with the
   source AND provenance count when the steps come from a source that
   started mid-day:
   - Zepp only → "🍋 Zepp"
   - Sensor only → "📡 Sensor (desde HH:MM)" where HH:MM is the
     timestamp captured when the baseline was taken
   - Both: "🍋 Zepp + 📡 Sensor" (rare)
   - None → "—"

2. **Subtitle below the steps value**, when the source is sensor and
   the baseline was captured today on open (not on first sensor event),
   show a subtle lemon-tinted caption: "Passos pré-abertura também
   contabilizados" (or shorter "Inclui passos antes de abrir").

No new layouts, no new colors, no animations — minimal change inside
the existing `fragment_today.xml` ids.

## Files

| Layer | File | Change |
|---|---|---|
| Data | `app/src/main/java/com/stepwatch/app/StepRepository.kt` | +`refreshMidnightBaseline()` private fun, called from `readNativeStepsToday()`; +`midnightCapturedAt: Long?` field set when capture runs in this session (used for the pill timestamp) |
| UI | `app/src/main/java/com/stepwatch/app/TodayFragment.kt` | Update `source_pill` text + new subtitle TextView for the pre-open caption |
| Layout | `app/src/main/res/layout/fragment_today.xml` | Add `subtitle_capture` TextView under `steps_value` (visibility=GONE by default) |
| Strings | `app/src/main/res/values/strings.xml` | +`source_sensor_captured` (with `time`), +`pre_open_caption` |
| Tests | `app/src/test/java/com/stepwatch/app/StepRepositoryTest.kt` | +2 JVM tests (acceptance criteria above) |
| ADR | `decisions/0009-baseline-on-resume.md` | New ADR documenting the decision and the matrix |
| Spec | `spec.md` | Update Goal paragraph to mention "baseline captured at app-open" |

## How to verify on device

1. Install the APK from the PR's CI artifact (`StepLemon-debug`).
2. Yesterday: open the app, walk ~30 steps.
3. Sleep / leave the device alone overnight (lastRawTotal stays
   frozen — prefs preserved).
4. Today at 06h00: open the app WITHOUT walking first.
5. The Today screen should show steps = 0 but with the pill
   "📡 Sensor (desde 06:00)" — wait, that's the baseline timestamp
   not a step count. Steps still = 0 until first step.
6. Walk 20 steps. Donut updates, steps show 20, and the caption
   "Passos pré-abertura também contabilizados" appears because the
   baseline was captured at open (06:00), not at the first sensor event
   (06:08 when the user moved).

Important: between step 4 and step 5 the user must see SOMETHING — the
pill updates to show the captured timestamp. Without this fix the pill
just says "Sensor" without a time, indistinguishable from "first open
ever, no baseline yet".

## DSH / review

DSH dispatched via `~/bin/dsh-dispatch --workspace ~/projetos/StepLemon
--task '...'` for a single file-level review (Ponytail +
Standards+Spec). Hermes applies findings, then merge with
`--admin --squash --delete-branch`.
