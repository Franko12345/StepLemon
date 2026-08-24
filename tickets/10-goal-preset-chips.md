# Ticket 10 — Goal preset chips (3k / 5k / 8k / 10k / 12k / 15k / 20k)

- **Status:** ✅ Done (merged in PR #4, commit `8aedf14`)
- **Date:** 2026-08-24
- **Spec ref:** [spec.md → Settings](../spec.md#screens) (subsection 4)
- **Owner:** Franco
- **Depends on:** 04

## Goal

Add a row of preset chips above each of the 3 sliders in the Settings screen.

## Acceptance criteria

- [x] Each goal section (Min, Daily, Stretch) shows 5–7 preset chips with
      commonly-used round values.
- [x] Tapping a chip sets the goal to exactly that value (no rounding).
- [x] The chip corresponding to the current goal is visually highlighted
      (lime/green border or filled background).
- [x] Sliders remain functional; dragging the slider past a preset updates
      the highlight as needed.
- [x] Presets are:
 - **Min**: 1k, 2k, 3k, 5k, 7k
 - **Daily**: 3k, 5k, 8k, 10k, 12k, 15k, 20k
 - **Stretch**: 10k, 12k, 15k, 20k, 25k, 30k
- [x] No new dependencies.
- [x] APK still <10 MB.

## Files

- `app/src/main/res/layout/fragment_settings.xml` (added `chips_min_anchor`,
  `chips_daily_anchor`, `chips_stretch_anchor` LinearLayout placeholders)
- `app/src/main/java/com/stepwatch/app/SettingsFragment.kt`
  (`addPresetChipsAbove`, `refreshChipHighlights`, `formatPresetLabel`)
- `app/src/main/res/drawable/bg_goal_chip.xml` (new — selected/pressed/default)

## Implementation note

Chips are built in Kotlin (not XML) via `addPresetChipsAbove(...)` so they
share one code path across all three sliders. The chip click sets the
SeekBar's `progress`, which fires `onProgressChanged` → writes to
SharedPreferences → updates the highlight. This means the chip code does
not need its own listener for state changes; the SeekBar owns the truth.

## Smoke

1. Open Settings → tap "10k" chip on Daily section → value jumps to exactly 10000.
2. Drag the slider to 9999 → no chip highlighted.
3. Drag to 10000 → "10k" chip highlighted again.
4. APK <10 MB.

## Status update history

- 2026-08-24: shipped in PR #4, marked ✅ Done.