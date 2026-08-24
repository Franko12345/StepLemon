# Triage labels

The five canonical labels below are configured on this repo. Don't create
new labels without a good reason; the `triage` skill reads from these.

| Label | Color | Description |
|-------|-------|-------------|
| `needs-triage` | `#fbca04` (yellow) | New issue; maintainer hasn't read it yet. |
| `needs-info` | `#cccccc` (gray) | Waiting on the reporter for clarification. |
| `ready-for-agent` | `#0e8a16` (green) | Clear scope; safe to dispatch a coding sub-agent. |
| `ready-for-human` | `#1d76db` (blue) | Needs a human decision (UX, API choice, …). |
| `wontfix` | `#ffffff` (light gray) | Acknowledged but not in scope (see ADR / spec.md). |

## Mapping

| Triage state | Maps to label |
|--------------|---------------|
| Just filed, no maintainer eyes | `needs-triage` |
| Reporter gave vague / ambiguous info | `needs-info` |
| Acceptance criteria + files + smoke documented | `ready-for-agent` |
| Needs product / UX / API call decision | `ready-for-human` |
| Out of scope, won't implement | `wontfix` |

## State machine

```
          ┌──────────────────┐
          │   needs-triage   │
          └────────┬─────────┘
                   │ maintainer reads
       ┌───────────┼────────────────────┐
       ▼           ▼                    ▼
 needs-info  ready-for-agent     ready-for-human
       │           │                    │
       │           │ done               │ done
       │           ▼                    ▼
       │      (merged PR)          (decision recorded
       │           │                in ADR or spec.md)
       │           ▼                    ▼
       └──────► (auto-close) ◄──────────┘
                   ▲
                   │
              wontfix (with comment)
```

## Re-creating the labels

If you fork or re-create this repo, run:

```bash
for label in needs-triage needs-info ready-for-agent ready-for-human wontfix; do
  gh label create "$label" --color "<see table>" --description "<see table>" --force
done
```

Replace `<see table>` with the matching hex code and description from the
table above.