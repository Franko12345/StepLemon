# Ticket 10 — Goal preset chips (3k / 5k / 8k / 10k / 12k / 15k / 20k)

- **Status:** 🟡 In progress
- **Date:** 2026-08-24
- **Spec ref:** [spec.md → Settings](../spec.md#screens) (subsection 4)
- **Owner:** Hermes
- **Depends on:** 04

## Problem

The Settings screen has 3 `SeekBar` controls (Min / Daily / Stretch goal).
`SeekBar` resolves to integer `progress`, so dragging for a precise value
like 5000 is hard — users end up at 5014 or 4986 and can't easily fine-tune.

The Stepmelon app already solves this with **preset chips** above each
slider: tapping a chip snaps the value to that preset.

## Goal

Add a row of preset chips above each of the 3 sliders in the Settings screen.

## Acceptance criteria

- [ ] Each goal section (Min, Daily, Stretch) shows 5–7 preset chips with
      commonly-used round values.
- [ ] Tapping a chip sets the goal to exactly that value (no rounding).
- [ ] The chip corresponding to the current goal is visually highlighted
      (lime/green border or filled background).
- [ ] Sliders remain functional; dragging the slider past a preset updates
      the highlight as needed.
- [ ] Presets are:
 - **Min**: 1k, 2k, 3k, 5k, 7k
 - **Daily**: 3k, 5k, 8k, 10k, 12k, 15k, 20k
 - **Stretch**: 10k, 12k, 15k, 20k, 25k, 30k
- [ ] No new dependencies.
- [ ] APK still <10 MB.

## Files

- `app/src/main/res/layout/fragment_settings.xml` (add chip rows)
- `app/src/main/java/com/stepwatch/app/SettingsFragment.kt` (wire click handlers)
- `app/src/main/res/drawable/bg_goal_chip.xml` (new — selected/unselected state)

## Smoke

1. Open Settings → tap "10k" chip on Daily section → value jumps to exactly 10000.
2. Drag the slider to 9999 → no chip highlighted.
3. Drag to 10000 → "10k" chip highlighted again.
4. APK <10 MB.

## Notes

- Chips are pure UI; they don't bypass the SeekBar logic (the SeekBar's
  `onProgressChanged` is what writes to prefs). The chip click simply
  computes the SeekBar's `progress` for the preset and sets it programmatically.
- Highlight logic should be in the same place as the slider's
  `onProgressChanged` callback to keep them in sync.