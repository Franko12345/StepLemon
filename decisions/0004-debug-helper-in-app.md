# ADR 0004: In-app debug helper for Zepp schema discovery

- **Status:** Accepted
- **Date:** 2026-08-24
- **Decider:** Franco Valois Delucca

## Context

`com.xiaomi.hm.health.HMProvider`'s `day_total_summary` provider has an undocumented schema that varies across MIUI 12 / MIUI 13 / HyperOS 1.x and across Zepp versions. Reverse-engineering via GitHub search is incomplete: most public references are 3+ years old (MIUI 10–11 era).

To finalize `StepRepository.parseDailyCursor()` we need the **actual schema from a real device running a current Zepp install**. Options:

1. **External `adb shell content query --uri ...`** — works but requires the user to install ADB drivers, connect the phone over USB, and paste the output. High friction.
2. **Logcat logging from a one-off debug build** — same friction as #1 plus a custom APK.
3. **In-app button that dumps the schema and shares it via Intent** — zero friction. User opens the app, taps a button, shares the text via Telegram (which is the primary support channel anyway).

## Decision

**Add an in-app debug button that runs `StepRepository.dumpZeppSchema()` and shares the result via `Intent.ACTION_SEND`.**

The button lives at the bottom of the **Settings** screen, styled as a `TextButton` (visually subordinate to production controls, but discoverable). It is **always present in the build** — no separate debug-build variant — because the cost is ~40 lines of code and the diagnostic value is permanent.

The schema dump is plain text:
```
=== Zepp HMProvider dump ===
package installed: true
authorized: true

--- day_total_summary ---
columns (3): data_hr, data, summary
row[0]: data_hr=... data=... summary={"v":6,...}

--- step_counter ---
columns (4): date, step, ...
row[0]: date=2026-08-24 step=...
```

## Consequences

**Positive**
- Zero-friction schema discovery. User shares one tap via Telegram; we adapt the parser in the next PR.
- Same mechanism can debug other Zepp providers (e.g. `sleep`, `heart_rate`) by extending the list.
- Works in CI smoke tests too — `dumpZeppSchema()` can be invoked from JVM tests with a mock `ContentResolver`.

**Negative**
- One more button on the Settings screen. Could be hidden behind a long-press on the version label, but discoverability matters more for a single-developer project.
- The dump reveals the app's *exact* provider URIs to anyone with the APK. Acceptable: those URIs are already visible in `AndroidManifest.xml` and known from reverse-engineered reports.

**When this ADR should be revisited**
- When we ship to the Play Store and want a clean prod UI. The button can be hidden via a `BuildConfig.DEBUG` check then.
- When we add more Zepp providers and want a debug screen instead of a single button.

**Migration path**
- Replace the button with a dedicated `DebugActivity` (scrollable text + per-provider copy buttons) when the dump gets longer than ~30 lines.