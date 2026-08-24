# 🍋 StepLemon — Product Spec

> **Status:** v1.3 (released). Last meaningful change: v1.3 fixes the "Stats
> doesn't include today" bug (PR #6, DSH-authored 4-layer fix — see ADR 0006).
> Next: v1.4 will adapt `parseDailyCursor()` to the real Zepp schema once the
> user shares the in-app dump.

## Goal

A privacy-first, lemon-themed step counter for Xiaomi/HyperOS phones that
reads steps from the Zepp Life (Mi Fitness) ContentProvider when authorized
and falls back to Android's native `TYPE_STEP_COUNTER` sensor. No login,
no cloud, no telemetry.

Inspired by Stepmelon's UX; themed in 🍋.

## Non-goals (explicit)

- **No login / no account.** The app never sees the user's Xiaomi credentials.
- **No cloud sync.** All state is local (SharedPreferences). Multi-device is out.
- **No Google Fit / Health Connect integration.** Those are privacy tradeoffs
  the project doesn't take.
- **No widget.** First-class app only. A widget is a v2 stretch.
- **No native background service.** MIUI/HyperOS aggressively kill those,
  and they need a visible notification. The Zepp provider gives us historical
  data anyway. See [`decisions/0002-no-background-service.md`](decisions/0002-no-background-service.md).
- **No workout / sleep / heart-rate / SpO2.** Only step count. Other metrics
  live in Zepp itself.
- **No premium / paywall.** Stepmelon has a "🔒 Premium" tier — we don't.

## Target user

- Owns a Xiaomi / Redmi / Poco phone (MIUI 12+ or HyperOS 1.x).
- Has Zepp Life (Mi Fitness) installed and signed in, **or** is willing to use
  the native sensor.
- Privacy-conscious: doesn't want a third-party step counter phoning home.
- Likes cute 🍋 lemon UI instead of corporate pink.

## UX

### Screens

1. **Today** (default tab) — Triple-ring donut centered, 3 goal cards below,
   3 stat cards (streak / distance / calories) at the bottom. Source pill in
   the top right (green = Zepp, gray = sensor). Auto-refreshes every 2 seconds.
2. **Stats** — Weekly bar chart (last 7 days), lifetime totals (steps,
   distance, goals met, days tracked, current streak, longest streak),
   personal-best cards (most steps, longest streak). Today's progress is
   **always included** in the lifetime total (v1.3 fix).
3. **History** — Period chips (7d / 30d / 90d / all), 3 summary cards,
   per-day list with progress bar (lime if met goal, pink otherwise) and
   ✅ for met-goal days. Today is the first row.
4. **Settings** — 3 sliders for goal values, each with **preset chips** above
   (Min: 1k/2k/3k/5k/7k; Daily: 3k/5k/8k/10k/12k/15k/20k;
   Stretch: 10k/12k/15k/20k/25k/30k), data source pill, Zepp status, reset
   button, about text, and a **hidden debug button** to dump Zepp's
   ContentProvider schema.

### Theme

- Dark base `#0E1411` (slightly green-tinted).
- Lemon yellow `#D4E157` (primary accent).
- Lime green `#9CCC65` (secondary accent, used for "min goal" ring).
- Pink `#F472B6` (sparing — stretch goal, in-bar misses).
- Cyan / orange / yellow for stat card icons.
- Adaptive icon: lemon vector with stylized footprint overlay.

### Persistence

- All goals in `SharedPreferences("stepwatch_goals")`.
- Sensor baseline (last raw + midnight anchor) in `SharedPreferences("stepwatch")`.
- App-data survives kill and reboot. No file storage; no DB.

## Technical choices

| Choice | Value | Why |
|--------|-------|-----|
| Language | Kotlin 1.9.22 | First-class on Android, null-safe |
| UI | Native Android Views + XML layouts | No Compose deps, ~5MB smaller APK; see [ADR 0003](decisions/0003-native-android-ui.md) |
| Min SDK | 23 (Android 6.0) | Covers >99% of active devices |
| Target SDK | 34 (Android 14) | Required for Play Store as of Aug 2024 |
| Build | AGP 8.2.2 + Gradle 8.5 + JDK 17 | Stable, headless CI-friendly |
| Data source 1 | `com.xiaomi.hm.health.HMProvider` | Zepp's ContentProvider — historical daily totals, no Zepp-side rate limits |
| Data source 2 | `Sensor.TYPE_STEP_COUNTER` (Android stdlib) | Universal fallback; works without Zepp; see [ADR 0001](decisions/0001-zepp-vs-sensor.md) |
| Charts | Custom `View` subclasses (`TripleDonutView`, `BarChartView`) | No MPAndroidChart dep (~5MB saved); no proguard headaches |
| Persistence | `SharedPreferences` | 4 keys, no need for Room |
| Tests | JUnit4 + Mockito-core (testImplementation only) | See [ADR 0005](decisions/0005-mockito-for-tests.md) |
| CI | GitHub Actions on every push/PR | Build debug APK + upload as artifact. 2 min runs. |
| Heavy work | DSH (DeepSeek Harness) on Proxmox LXC | Offload multi-file refactors. See [ADR 0007](decisions/0007-dsh-dispatch-workflow.md) |

## Open questions

- **What does the Zepp `day_total_summary` provider actually expose?** Current
  parser is a best-effort table-driven guess (day/date column × step/total
  column × 3 date formats). Ticket 06 will adapt if needed; v1.3 already
  works without Zepp (sensor-only path).
- **Should we add a Health Connect writer** so other apps can see the data?
  Currently out of scope (see Non-goals).
- **Notification when daily goal is met?** Tempting but it's a battery
  tradeoff and we don't have a persistent service. Punt to v3.

## Out of scope (explicit)

- iOS / iPadOS
- Android Auto
- Wear OS companion
- Tablet-specific layouts (works but isn't optimized)
- Badge / achievement system (ticket 07, won't fix this cycle)
- Sleep / heart-rate / SpO2 reading (ticket 08, won't fix this cycle)

## See also

- [ADRs in `decisions/`](decisions/) — architectural choices with rationale
- [Tickets in `tickets/`](tickets/) — each feature as a vertical slice
- [Build & dev conventions in `AGENTS.md`](AGENTS.md)