# Ticket 03 — Lemon theme

- **Status:** ✅ Done
- **Date:** 2026-08-24
- **Spec ref:** [spec.md → Theme](../spec.md#theme)
- **Owner:** Franco
- **Depends on:** none

## Goal

Replace the dark Material 3 default with a lemon-themed palette.

## Acceptance criteria

- [x] All colors moved to `colors.xml` with `lemon_*` prefix.
- [x] Background `#0E1411`, surface `#152019`, primary `#9CCC65` (lime), secondary `#D4E157` (lemon yellow), stretch `#F472B6`.
- [x] All layout files use color refs (no hardcoded hex).
- [x] App name changed to **StepLemon** in `strings.xml`.
- [x] Adaptive icon is a lemon vector with stylized footprint overlay.
- [x] BottomNav icons recolor correctly via `bottom_nav_tint` selector.

## Files

- `app/src/main/res/values/colors.xml` (rewritten)
- `app/src/main/res/values/themes.xml` (rewritten)
- `app/src/main/res/values/strings.xml` (app_name updated)
- `app/src/main/res/color/bottom_nav_tint.xml` (new)
- `app/src/main/res/drawable/ic_launcher_foreground.xml` (lemon vector)
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` (ref)

## Smoke

```bash
gradle assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
# Open the app. Confirm: green bottom-nav active, lemon-colored chart bars, dark surface.
```

## Notes

- Adaptive icon foreground is a 108dp vector; ensure `mipmap-anydpi-v26/ic_launcher.xml` references it correctly. Android 8+ uses adaptive; older falls back to the same drawable.