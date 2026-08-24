# ADR 0003: Native Android Views, not Jetpack Compose

- **Status:** Accepted
- **Date:** 2026-08-24
- **Decider:** Franco Valois Delucca

## Context

The choice between Jetpack Compose and the classic View/XML system is the single biggest architectural decision for any modern Android app. For StepLemon:

- Compose is the **recommended** path for new apps by Google as of 2024.
- Compose adds ~1.5–2MB to the APK and requires Kotlin compiler plugins.
- The UI here is **simple**: 4 screens, ~20 distinct views total, 2 custom `View` subclasses for charts.
- The build environment is **headless CI** (no Android Studio). Compose adds friction to headless builds (no live preview, harder to validate layouts without an emulator).

## Decision

**Use the View system with XML layouts. No Compose dependency.**

All layouts are XML in `app/src/main/res/layout/`. All UI logic uses `findViewById` (currently) or ViewBinding (a future single-conversion sweep — see agents.toml invariants).

Custom `View` subclasses handle the two charts:
- `TripleDonutView` — three concentric rings.
- `BarChartView` — bar chart with target dashed line.

## Consequences

**Positive**
- APK stays at ~5–6 MB (currently 5.7 MB).
- No `androidx.compose.*` deps in `build.gradle`.
- Existing Android devs can read the code without learning Compose.
- All resource-driven (theming, dark mode, RTL) for free.
- ProGuard / R8 shrinks cleanly with default rules.

**Negative**
- More boilerplate per screen (`Fragment` + `onCreateView` + `onViewCreated` + `findViewById` calls).
- No live preview in CI; we rely on the actual emulator/device for visual smoke. The local dev VM does not have one; visual smoke is done on the target Poco M5.
- If we add anything complex later (e.g. animated charts, drag-and-drop), Compose would be more concise. Punt to v3.

**When this ADR should be revisited**
- When Google deprecates the View system (no signal of this as of Aug 2026).
- When we add a screen with >10 dynamic list items that needs per-item animation. (Compose is genuinely better there.)

**Migration path**
- A future single commit can add Compose alongside View; they're interoperable. Not needed today.