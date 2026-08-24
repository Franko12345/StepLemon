# Ticket 05 — Debug button: dump Zepp schema

- **Status:** ✅ Done (merged in PR #4, commit `8aedf14`)
- **Date:** 2026-08-24
- **Spec ref:** [ADR 0001](../decisions/0001-zepp-vs-sensor.md), [ADR 0004](../decisions/0004-debug-helper-in-app.md)
- **Owner:** Franco
- **Depends on:** 04
- **Blocks:** 06

## Goal

Add a Settings button that runs `StepRepository.dumpZeppSchema()` and shares
the result via `Intent.ACTION_SEND`.

## Acceptance criteria

- [x] `StepRepository.dumpZeppSchema()` returns a string with: package installed flag, authorization flag, per-provider column list, per-provider first sample row.
- [x] The dump covers at least: `day_total_summary`, `step_counter`.
- [x] `SettingsFragment` has a "Debug: schema Zepp" button (`R.id.btn_debug_zepp`).
- [x] Tapping the button fires an `ACTION_SEND` chooser with the dump as `Intent.EXTRA_TEXT`.
- [x] Failures are reported in the dump (`SecurityException`, `Exception`) rather than thrown.
- [ ] **User has shared the dump via Telegram.** _(Still waiting — see ticket 06.)_

## Files

- `app/src/main/java/com/stepwatch/app/StepRepository.kt` (`dumpZeppSchema`)
- `app/src/main/java/com/stepwatch/app/SettingsFragment.kt` (button handler)
- `app/src/main/res/layout/fragment_settings.xml` (`btn_debug_zepp`)

## Smoke

```bash
gradle assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
# Open the app → Settings → tap "Debug: schema Zepp".
# Pick Telegram in the chooser, send to the maintainer.
```

## Next

When the user shares the dump, ticket **06** (`06-zepp-parser-finalize.md`)
adapts the parser to the real schema.

## Status update history

- 2026-08-24: button shipped in PR #4, marked ✅ Done.