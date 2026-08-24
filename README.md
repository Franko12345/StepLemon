# 🍋 StepLemon

A privacy-first step counter for Xiaomi / MIUI / HyperOS phones. Reads steps
from **Zepp Life (Mi Fitness)** when installed and authorized, and falls back
to the **Android `TYPE_STEP_COUNTER` sensor** otherwise. No login, no cloud,
no tracking.

> 🍋 *Theme: lemon. UX inspired by Stepmelon.*

## Goal

Give the user a clear, fofinha (cute), glanceable view of today's steps with
goal tracking, weekly history, and lifetime stats — without giving up privacy
to a third-party app.

Read [`spec.md`](./spec.md) for the full product spec.

## Status

- **v1.3 (current):** Stats tab now correctly includes today's step count.
  See [ADR 0006](decisions/0006-4-layer-bug-fix.md) for the four-layer
  fix that landed in [PR #6](https://github.com/Franko12345/StepLemon/pull/6).
- **CI:** every push to `main` builds the debug APK in ~2 min.
- **Heavy work:** dispatched to DSH (DeepSeek Harness) on the Proxmox LXC
  for multi-file refactors. See [ADR 0007](decisions/0007-dsh-dispatch-workflow.md).

## Quickstart

### Build (Option A — GitHub Actions, no local toolchain needed)

Every push to `main` triggers `.github/workflows/android-debug.yml`,
which builds the debug APK in ~2 minutes and uploads it as a CI artifact
named `StepLemon-debug`. Download from the run's page:

```
https://github.com/Franko12345/StepLemon/actions/workflows/android-debug.yml
```

Latest successful run: see the Actions tab.

### Build (Option B — Local, headless, no Android Studio)

```bash
# One-time toolchain bootstrap (JDK 17, cmdline-tools, build-tools, Gradle).
# Layout is documented in AGENTS.md → "Build environment".

# Build debug APK
cd ~/projetos/StepLemon
export JAVA_HOME=$HOME/sdk/jdk17
export ANDROID_HOME=$HOME/sdk
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH
./gradlew assembleDebug --no-daemon --console=plain
# → app/build/outputs/apk/debug/app-debug.apk
```

### Install on a device

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The app targets Android 14 (API 34) and runs on any device with API 23+.

### Run unit tests

```bash
./gradlew testDebugUnitTest
# Reports: app/build/reports/tests/testDebugUnitTest/index.html
```

## Layout

```
.
├── app/                 # Android Gradle module
├── decisions/           # ADRs (one per architectural choice)
├── tickets/             # Per-feature work units (see spec.md for ordering)
├── docs/agents/         # SDD configuration (issue tracker, domain layout)
├── spec.md              # Product spec — the source of truth
├── agents.toml          # Coding-agent persona profile
├── AGENTS.md            # Conventions for humans and coding agents
├── .github/workflows/   # GitHub Actions CI
└── LICENSE
```

## Documentation

- **[`spec.md`](./spec.md)** — Goal, non-goals, UX, technical choices, open questions.
- **[`AGENTS.md`](./AGENTS.md)** — How to work in this repo (build env, code style, DSH dispatch).
- **[`agents.toml`](./agents.toml)** — Dev persona + hard rules + review axes for coding agents.
- **[`decisions/`](./decisions/)** — Architecture Decision Records.
  - `0001-zepp-vs-sensor.md` — Why Zepp + sensor.
  - `0002-no-background-service.md` — Why no foreground service.
  - `0003-native-android-ui.md` — Why Views, not Compose.
  - `0004-debug-helper-in-app.md` — Why the in-app Zepp schema dump button.
  - `0005-mockito-for-tests.md` — Test framework choice.
  - `0006-4-layer-bug-fix.md` — The four-layer bug DSH found in v1.3.
  - `0007-dsh-dispatch-workflow.md` — How to dispatch work to DSH.
- **[`tickets/`](./tickets/)** — Each file is a vertical slice of work.
- **[`docs/agents/`](./docs/agents/)** — SDD config.

## How the project is built

1. **Tickets** are written in `tickets/` as vertical slices.
2. **Coding agents** (DSH for heavy work, Claude/Hermes for one-shots) implement the ticket.
3. **Pull request** opened with the ticket ID in the title.
4. **CI** builds the APK and uploads as artifact.
5. **Reviewer fan-out** runs in parallel (per `agents.toml [review_axes]`).
6. **Merge** after green CI + reviewer sign-off.
7. **Tickets marked ✅ Done** with the PR link.

## License

MIT. See [LICENSE](./LICENSE).