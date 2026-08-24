# Domain docs layout

This repo uses **single-context** layout:

- `CONTEXT.md` at the repo root (optional; not present yet — info lives in `spec.md` + `AGENTS.md`).
- `decisions/` at the repo root for ADRs.
- `tickets/` at the repo root for vertical-slice work units.

We do **not** use multi-context (CONTEXT-MAP.md + per-context CONTEXT.md) because
this is a single-module Android app — there's no monorepo signal here.

## Consumer rules

When opening a PR, the coding agent must:

1. **Read `spec.md` first.** It contains the Goal / Non-goals / UX / Technical
   choices / Open questions for the project.
2. **Read the relevant ticket** (`tickets/NN-*.md`) for acceptance criteria,
   files likely to change, and smoke steps.
3. **Read every ADR in `decisions/` whose ID is referenced** by the ticket or spec.
4. **Skim `AGENTS.md`** for build commands, code style, and the SDD workflow.

ADRs are append-only. To change a decision, write a new ADR that supersedes the
old one (e.g. `0010-revert-0004.md`) and update the old one's **Status** field
to "Superseded by ADR-NNNN".

## Ticket numbering

Tickets are numbered in **implementation order** (blockers first). When opening
a new ticket, pick the next available integer and link it from any dependent
tickets via `Depends on:` and `Blocks:` lines.