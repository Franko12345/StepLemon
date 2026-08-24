# 🍋 StepLemon

A privacy-first step counter for Xiaomi / MIUI / HyperOS phones. Reads steps from
**Zepp Life (Mi Fitness)** when installed and authorized, and falls back to the
**Android `TYPE_STEP_COUNTER` sensor** otherwise. No login, no cloud, no tracking.

> 🍋 *Theme: lemon. UX inspired by Stepmelon.*

## Goal

Give the user a clear, fofinha (cute), glanceable view of today's steps with
goal tracking, weekly history, and lifetime stats — without giving up privacy
to a third-party app.

Read [`spec.md`](./spec.md) for the full product spec.

## Quickstart

### Build

**Option A — GitHub Actions (no local toolchain needed)**

Every push to `main` triggers `.github/workflows/android-debug.yml`,
which builds the debug APK in ~2 minutes and uploads it as a CI artifact
named `StepLemon-debug`. Download from the run's page:

```
https://github.com/Franko12345/StepLemon/actions/workflows/android-debug.yml
```

Latest run: see [Actions tab](../../actions).

**Option B — Local (headless, no Android Studio)**

```bash
# One-time toolchain bootstrap (JDK 17, cmdline-tools, build-tools, Gradle)
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

## Layout

```
.
├── app/                 # Android Gradle module
├── decisions/           # ADRs (one per architectural choice)
├── tickets/             # Per-feature work units (see spec.md for ordering)
├── docs/
│   ├── agents/          # SDD configuration (issue tracker, domain layout)
│   └── screenshots/     # UX reference (Stepmelon screenshots)
├── spec.md              # Product spec — the source of truth
├── agents.toml          # Coding-agent persona profile
└── AGENTS.md            # Conventions for humans and coding agents
```

## Documentation

- **[`spec.md`](./spec.md)** — Goal, non-goals, UX, technical choices, open questions.
- **[`AGENTS.md`](./AGENTS.md)** — How to work in this repo (build env, conventions).
- **[`decisions/`](./decisions/)** — Architecture Decision Records. Read these before changing the stack.
- **[`tickets/`](./tickets/)** — Each file is a vertical slice of work. Some are done (✅), some are next (📋).
- **[`docs/agents/`](./docs/agents/)** — SDD config: how tickets and triage labels work in this repo.

## License

MIT. See [LICENSE](./LICENSE).