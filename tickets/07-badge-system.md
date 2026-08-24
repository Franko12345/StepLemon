# Ticket 07 — Badge / achievement system

- **Status:** 💤 Won't fix (this cycle)
- **Spec ref:** [spec.md → Non-goals](../spec.md#non-goals-explicit)

## Why this exists

Stepmelon has a "Badges" screen with 28 achievements. Users asked for parity.

## Why we're not doing it

1. **Out of scope** in `spec.md` (no premium / no gamification / no Zepp-write).
2. **Requires a writable schema** in Zepp (we don't have one). StepLemon is read-only by design.
3. **Scope creep risk** — badge logic, animations, tiered unlocks is a feature-sized project on its own. Better to ship as a separate app if it ships at all.
4. **Battery / UX** — surface-level badge notifications would need a service, which ADR 0002 forbids.

## What to do instead

- If gamification is wanted in the future, spin a `steplemon-badges` repo and integrate it as a separate app with its own data source.
- The existing Stats screen already shows lifetime totals + personal bests, which covers the "see your progress" motivation without a badge engine.

## If this ticket gets reopened

The minimum viable badge system would need:
- A new `BadgesFragment` and tab.
- 6-8 hardcoded badges (e.g. "First 1k day", "7-day streak", "10k day x5").
- A `BadgeStore` (SharedPreferences-backed set of unlocked IDs).
- Run on every fragment `onResume`.
- An ADR for the new tab and the badge unlocks.

Estimated effort: 4-6 hours. Defer until v2.