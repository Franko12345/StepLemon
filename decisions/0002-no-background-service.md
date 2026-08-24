# ADR 0002: No background service

- **Status:** Accepted
- **Date:** 2026-08-24
- **Decider:** Franco Valois Delucca

## Context

Android's canonical way to keep a sensor running while the user isn't looking at the app is a `Service` with `startForegroundService()` plus a persistent notification. On Xiaomi phones running MIUI/HyperOS, this is actively hostile:

- **MIUI's "Battery saver"** aggressively kills background services the moment it decides they're "unused". This happens even when the user has explicitly excluded the app from battery optimization. MIUI re-applies the kill after every OS update.
- **HyperOS's "Background app management"** is the same behavior rebranded. There's a "no restrictions" toggle, but it's hidden in three submenus and resets on every update.
- **Foreground services require a visible notification.** The user sees a permanent notification while the app is "running". That's bad UX for a step counter the user only opens once a day.

We also don't need a background service for the **primary use case**: Zepp's ContentProvider gives historical data on demand, so when the user opens StepLemon, it reads everything that happened since yesterday. No tracking in our app is needed.

## Decision

**No `Service` of any kind. No `WorkManager` periodic jobs. No foreground notification.**

The app only reads:
- `TYPE_STEP_COUNTER` while the user has it open (2-second refresh in `TodayFragment`).
- Zepp's provider on resume of any fragment.

When the user closes the app, we stop reading. We persist the last raw sensor value in `SharedPreferences` so the next open picks up where we left off within the same day.

## Consequences

**Positive**
- No battery cost when the app isn't open.
- No persistent notification cluttering the status bar.
- Survives MIUI's aggressive background kill without configuration.
- Aligns with the project's privacy stance: nothing runs, nothing is sent.

**Negative**
- **No "live" updates while the app is closed.** The user must open the app to see today's progress.
- The `TYPE_STEP_COUNTER` baseline is captured on first sensor event after midnight; steps taken **before** the user opens the app that day are invisible.
- Zepp-as-data-source covers this gap (it tracks 24/7 in its own service, which the user has implicitly authorized), so the gap is only present for users who don't have Zepp. For them, the answer is "open the app once after waking up".

**When this ADR should be revisited**
- If we add a "daily goal hit" notification. That's the only feature that genuinely needs a service, and even then we'd want a one-shot `WorkManager` job, not a foreground service.
- If MIUI stops killing background services (unlikely; this is a 7-year-old behavior).

**Migration path**
- Add `WorkManager` (no foreground notification needed) for the daily-goal-hit notification, scheduled at midnight + small jitter.