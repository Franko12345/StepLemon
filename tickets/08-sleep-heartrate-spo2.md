# Ticket 08 — Sleep / heart rate / SpO2 reading

- **Status:** 💤 Won't fix (this cycle)
- **Spec ref:** [spec.md → Non-goals](../spec.md#non-goals-explicit)

## Why this exists

Zepp exposes more than just steps. Sleep duration, heart rate, and SpO2 are
common requests.

## Why we're not doing it

1. **Scope creep.** Adding these expands StepLemon from "step counter" to
   "general fitness dashboard", which is a different product category.
2. **Schema uncertainty.** Same problem as ticket 06 but for 3 more providers.
3. **Privacy surface increases** — heart rate is biometric. Storing it locally
   needs stronger guarantees than `SharedPreferences` provides (encrypted Room
   or DataStore).
4. **No clear UI direction.** Stepmelon doesn't have these; we'd be inventing
   the UX from scratch.

## What to do instead

- If the user asks for these, point them to Zepp Life directly. The data is
  there with a richer UI than we could build.
- If we ever do it, spawn a separate `steplemon-health` repo with its own
  spec, ADR trail, and release cadence.