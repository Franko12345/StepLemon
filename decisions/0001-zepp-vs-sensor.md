# ADR 0001: Zepp ContentProvider first, native sensor as fallback

- **Status:** Accepted
- **Date:** 2026-08-24
- **Decider:** Franco Valois Delucca

## Context

StepLemon needs to read daily step totals on a Xiaomi Poco M5 running HyperOS 1.0.10. Two viable data sources exist on the device:

1. **Zepp Life (Mi Fitness) ContentProvider** — `content://com.xiaomi.hm.health.HMProvider/day_total_summary`. Aggregates steps server-side, persists them, gives historical daily totals (today, yesterday, last 30 days).
3. **Android `TYPE_STEP_COUNTER` sensor** — Hardware counter on the SoC. Cumulative since last device boot. No historical data; only "since the app started counting."

A third option — Xiaomi's HTTP API (`api-mifit.huami.com`) — exists but requires OAuth login with the user's Xiaomi account, which we explicitly reject in `spec.md` (non-goal: no login).

The Zepp provider needs the user to (a) install Zepp Life, (b) grant the StepLemon app access via Zepp's permission flow. The sensor needs Android 10+ runtime permission (`ACTIVITY_RECOGNITION`) and reads from any device with a step counter (universal).

## Decision

**Try Zepp first; fall back to native sensor on miss or unauthorized.**

`StepRepository.readStepsToday()` returns:
- Zepp's value if the package is installed and the provider query returns a non-empty row.
- Otherwise `Sensor.TYPE_STEP_COUNTER`-derived value (`lastRawTotal - midnightRawTotal`).
- Otherwise `0L` (UI shows "0" and a hint to install Zepp or grant the permission).

## Consequences

**Positive**
- Users with Zepp installed get the same UX as Stepmelon — historical data, multi-day views, accurate daily totals without opening the app first.
- Users without Zepp still get a working step counter; the only loss is historical view.
- Zero login; zero server round-trip.
- Survives Zepp being uninstalled mid-day (graceful degradation to sensor).

**Negative**
- Two code paths to maintain. Each must be tested independently.
- Zepp's provider schema is undocumented and has changed across MIUI versions (12 → 13 → HyperOS 1.x). Our parser is currently best-effort; ticket `05-debug-zepp-schema` collects the real schema from a real device.
- `TYPE_STEP_COUNTER` resets to 0 on device reboot, not at midnight. The current implementation anchors the baseline at the first sensor event after local midnight (see `rollMidnightIfNeeded()`), which works but means **steps taken before the user opens the app that day are invisible to the sensor source**.

**Migration path**
- If Zepp exposes a clean REST API in the future, we can swap providers without UI changes.
- If `Health Connect` becomes the canonical store, we can add a third source alongside.