# Ticket 01 — Native Android sensor fallback

- **Status:** ✅ Done
- **Date:** 2026-08-24
- **Spec ref:** [spec.md → Technical choices](../spec.md#technical-choices), [ADR 0001](../decisions/0001-zepp-vs-sensor.md)
- **Owner:** Franco

## Goal

Read today's step count from Android's `TYPE_STEP_COUNTER` sensor without any
Zepp install or login.

## Acceptance criteria

- [x] `StepRepository.readNativeStepsToday()` returns `Long` from `lastRawTotal - midnightRawTotal`.
- [x] `rollMidnightIfNeeded()` anchors a new baseline at local midnight without losing the existing value (uses SharedPreferences).
- [x] App handles the "no events yet today" case: returns `0L`, not `null`.
- [x] App handles device reboot: the cumulative counter resets on reboot, so `midnightRawTotal` is re-anchored on the first event after boot.
- [x] `resetToday()` lets the user manually re-anchor the baseline (UI button "Reset").
- [x] Android 10+ runtime permission for `ACTIVITY_RECOGNITION` is requested on first launch.

## Files

- `app/src/main/java/com/stepwatch/app/StepRepository.kt` (created)
- `app/src/main/AndroidManifest.xml` (added `ACTIVITY_RECOGNITION` permission + sensor features)

## Smoke

```bash
gradle assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
# Open the app on a device. Walk 50 steps. Value updates.
```

## Notes

- The Android sensor doesn't reset at midnight; it resets at device reboot. The midnight anchor is our workaround. **Steps taken before the user opens the app that day are invisible to this source.** Zepp covers that gap (see ADR 0001).