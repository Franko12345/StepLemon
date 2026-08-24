# Issue tracker

This repo uses **GitHub Issues** for tracking work.

- Project: https://github.com/Franko12345/StepLemon
- `gh issue create`, `gh issue list`, etc. work without extra config.
- The `gh` CLI is authenticated as `Franko12345` on the dev machine.

## Triage labels

The five canonical labels are configured on the repo (created via the
`setup-matt-pocock-skills` skill run on 2026-08-24):

| Label | Purpose |
|-------|---------|
| `needs-triage` | New issue; maintainer hasn't read it yet. |
| `needs-info` | Waiting on the reporter for clarification. |
| `ready-for-agent` | Clear scope; safe to dispatch a coding sub-agent. |
| `ready-for-human` | Needs a human decision (UX, API choice, …). |
| `wontfix` | Acknowledged but not in scope (see ADR / spec.md). |

The full mapping lives in [`docs/agents/triage-labels.md`](triage-labels.md).

## PRs as a request surface

PRs are **not** part of the triage queue. They live in the GitHub PR list
and are reviewed out-of-band. The `gh` workflow for a feature ticket is:

1. Maintainer creates issue, labels `ready-for-agent`.
2. Agent or human opens PR linked to the issue (`#NNN` in title and body).
3. PR gets two reviews (Ponytail + Standards+Spec) per `agents.toml`.
4. Maintainer merges; ticket auto-closes via PR link.