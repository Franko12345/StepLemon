# Ticket 04 — Multi-screen UI (Today / Stats / History / Settings)

- **Status:** ✅ Done
- **Date:** 2026-08-24
- **Spec ref:** [spec.md → Screens](../spec.md#screens)
- **Owner:** Franco
- **Depends on:** 02, 03

## Goal

Replace the single-screen layout with a 4-tab bottom-navigation app matching the
Stepmelon UX pattern.

## Acceptance criteria

- [x] `MainActivity` hosts a `FragmentContainerView` and a `BottomNavigationView`.
- [x] 4 fragments: `TodayFragment`, `StatsFragment`, `HistoryFragment`, `SettingsFragment`.
- [x] **Today**: triple-ring donut (3 goal rings) + 3 goal cards + 3 stat cards. Auto-refreshes every 2s.
- [x] **Stats**: weekly bar chart (`BarChartView`) + lifetime totals list (6 rows) + 2 personal-best cards.
- [x] **History**: period chips (7d/30d/90d/all) + 3 summary cards + per-day list with progress bar.
- [x] **Settings**: 3 sliders for goals (Min 1k–20k, Daily 2k–30k, Stretch 5k–30k) + data source pill + Zepp status + reset button + about card.
- [x] Tapping a goal card on Today navigates to Settings tab (deep-link inside the app).
- [x] All UI strings live in `strings.xml` (no hardcoded English).
- [x] Empty state (Zepp unavailable) is handled in Stats and History without crashing.

## Files

- `app/src/main/java/com/stepwatch/app/MainActivity.kt`
- `app/src/main/java/com/stepwatch/app/TodayFragment.kt`
- `app/src/main/java/com/stepwatch/app/StatsFragment.kt`
- `app/src/main/java/com/stepwatch/app/HistoryFragment.kt`
- `app/src/main/java/com/stepwatch/app/SettingsFragment.kt`
- `app/src/main/java/com/stepwatch/app/TripleDonutView.kt` (custom view)
- `app/src/main/java/com/stepwatch/app/BarChartView.kt` (custom view)
- `app/src/main/java/com/stepwatch/app/ColorUtil.kt` (helper)
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/layout/fragment_today.xml`
- `app/src/main/res/layout/fragment_stats.xml`
- `app/src/main/res/layout/fragment_history.xml`
- `app/src/main/res/layout/fragment_settings.xml`
- `app/src/main/res/layout/item_history.xml`
- `app/src/main/res/menu/bottom_nav.xml`
- `app/src/main/res/drawable/bg_*.xml`, `ic_*.xml` (multiple)

## Smoke

```bash
gradle assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
# Tap each bottom-nav tab. Confirm content renders and scrolls.
# Open Settings → move a slider → switch back to Today → confirm donut reflects new goal.
```

## Notes

- `findViewById` is used throughout. ViewBinding is a future single-conversion sweep (see `agents.toml` invariants).
- `StatsFragment.calculateCurrentStreak()` may iterate up to 60 days; trivial cost but flag in review if changed.
- History items render with `bg_progress_bar_done.xml` when goal met, `bg_progress_bar.xml` otherwise.