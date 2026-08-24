# AGENTS.md — How to work in this repo

This file is read by humans and by coding agents (Claude, DSH, Hermes, …).
Keep it short. Detailed rationale lives in `decisions/`.

## Project at a glance

StepLemon is an Android step counter for Xiaomi/MIUI/HyperOS phones. It reads
steps from the Zepp Life (Mi Fitness) ContentProvider when authorized, with
the Android `TYPE_STEP_COUNTER` sensor as a fallback. UI is native Views
(no Compose), charts are hand-rolled custom Views (no third-party chart libs).
Themed in 🍋 lemon — see [spec.md](./spec.md) for the full product spec.

## Build environment

Headless CI-friendly. The expected local toolchain layout (already installed
on the dev VM):

```
$HOME/sdk/
├── jdk17/                  # OpenJDK 17 portable
├── cmdline-tools/latest/   # Android cmdline-tools (sdkmanager)
├── build-tools/34.0.0/     # aapt2, d8, zipalign, apksigner
├── platforms/android-34/   # android.jar
└── platform-tools/         # adb, fastboot
$HOME/sdk/gradle-8.5/bin/   # Gradle 8.5 (added to PATH)
```

Bootstrap on a fresh box (run once, as the user):

```bash
# 1. JDK
curl -L -o /tmp/jdk.tar.gz "https://download.java.net/java/GA/jdk17.0.2/dfd4a8d0985749f896bed50d7138ee7f/8/GPL/openjdk-17.0.2_linux-x64_bin.tar.gz"
mkdir -p ~/sdk && tar -xzf /tmp/jdk.tar.gz -C ~/sdk && mv ~/sdk/jdk-17.0.2 ~/sdk/jdk17

# 2. Android cmdline-tools
curl -L -o /tmp/cmdtools.zip "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
mkdir -p ~/sdk/cmdline-tools/latest && unzip -q /tmp/cmdtools.zip -d /tmp/cmdtools && \
  mv /tmp/cmdtools/cmdline-tools/{bin,lib,NOTICE.txt,source.properties} ~/sdk/cmdline-tools/latest/

# 3. SDK packages
export JAVA_HOME=$HOME/sdk/jdk17
export ANDROID_HOME=$HOME/sdk
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH
yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"

# 4. Gradle
curl -L -o /tmp/gradle.zip "https://services.gradle.org/distributions/gradle-8.5-bin.zip"
unzip -q /tmp/gradle.zip -d ~/sdk
```

## Common commands

```bash
# Build debug APK
export JAVA_HOME=$HOME/sdk/jdk17 ANDROID_HOME=$HOME/sdk
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$HOME/sdk/gradle-8.5/bin:$PATH
./gradlew assembleDebug --no-daemon --console=plain
# → app/build/outputs/apk/debug/app-debug.apk

# Run unit tests
./gradlew testDebugUnitTest

# Validate APK
$ANDROID_HOME/build-tools/34.0.0/aapt dump badging app/build/outputs/apk/debug/app-debug.apk | head
$ANDROID_HOME/build-tools/34.0.0/apksigner verify app/build/outputs/apk/debug/app-debug.apk

# Install on a connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Tail the StepWatch log
adb logcat -s StepWatch:V
```

## CI

Every push to `main` and every PR triggers
[`.github/workflows/android-debug.yml`](.github/workflows/android-debug.yml),
which builds the debug APK in ~2 min and uploads it as the
`StepLemon-debug` artifact (14-day retention). The workflow:

- Uses `actions/checkout@v4`, `actions/setup-java@v4` (Temurin 17),
  `android-actions/setup-android@v3` (with explicit `packages` input —
  `api-level` / `build-tools-version` were removed in v3).
- Caches `~/.gradle/caches` between runs.
- Runs `./gradlew assembleDebug`.
- Verifies the APK with `aapt dump badging` + `apksigner verify`.
- Enforces the 10 MB size budget from `agents.toml [verification]`.

Download artifacts from the Actions tab.

## Code style

- **Kotlin**: idiomatic; prefer `val` over `var`; no `!!` unless justified
  with a `// safe: ...` comment.
- **Android Views, not Compose**. Don't add Compose dependencies — see
  [`decisions/0003-native-android-ui.md`](decisions/0003-native-android-ui.md).
- **Custom Views for charts** (`TripleDonutView`, `BarChartView`). Don't add
  MPAndroidChart or any chart library — it's ~5MB and we have 2 charts.
- **No new third-party deps** without an ADR. The whole app is
  AndroidX + Material + JUnit4 + Mockito-core (test-only). Keep it that way.
- **Strings go in `strings.xml`**. Don't hardcode English in Kotlin.
- **Colors go in `colors.xml`**. Don't hardcode `#XXXXXX` in layouts.
- **No Elvis (`? :`) on the Zepp-vs-native merge path.** Use explicit
  `when {}` with a zero check. See ADR 0006 for the four-layer bug
  that was caused by an Elvis misread.

## Spec-driven development (SDD)

This repo follows the [Matt Pocock SDD workflow](../spec.md):

1. Read `spec.md` first — it's the contract.
2. Pick a ticket from `tickets/` — each is a vertical slice (≤30 min).
3. Write code + a smoke test in the ticket's Files section.
4. Open a PR with the ticket ID in the title (`[#05] Add debug schema dump`).
5. Two reviewers run in parallel: Ponytail (over-engineering) and Standards+Spec.
   See `agents.toml` for the prompt template.
6. Address findings, then merge.

Tickets are numbered in implementation order (blockers first). Read each
ticket's "Spec ref" to know which spec section you're implementing.

## Dispatching heavy work to DSH

For multi-file refactors or cross-cutting bugs (4+ files, multiple
interacting layers), dispatch to the **DeepSeek Harness (DSH)** running
on the Proxmox LXC at `dsh.homelab:3080` (CT 200):

```bash
~/bin/dsh-dispatch \
  --workspace ~/projetos/StepLemon \
  --task "Goal: ... Spec ref: ... Acceptance: ... Branch: <exact-name> ... Files: ... Verification: ..." \
  --timeout 1500
```

See [`decisions/0007-dsh-dispatch-workflow.md`](decisions/0007-dsh-dispatch-workflow.md)
for the full operational lessons, including the force-push gotcha.

**The skill `deepseek-harness-dispatch` is the canonical reference.**
The shim `~/bin/dsh-dispatch` is the actual tool. Use the v2 names
(`--proxmox-host`, `--lxc-id`, etc.).

## Files likely to change

| Layer | Files |
|-------|-------|
| Data | `app/src/main/java/com/stepwatch/app/StepRepository.kt` |
| UI | `app/src/main/java/com/stepwatch/app/*Fragment.kt`, `app/src/main/res/layout/*.xml` |
| Theme | `app/src/main/res/values/{colors,themes,strings}.xml` |
| Manifest | `app/src/main/AndroidManifest.xml` |
| Build | `app/build.gradle`, root `build.gradle`, `settings.gradle` |
| Tests | `app/src/test/java/com/stepwatch/app/StepRepositoryTest.kt` |
| ADRs | `decisions/NNNN-*.md` (append-only, use new numbers for new decisions) |
| Tickets | `tickets/NN-*.md` (Status field updated as work progresses) |