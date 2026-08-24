# ADR 0007: DSH (DeepSeek Harness) dispatch workflow

- **Status:** Accepted
- **Date:** 2026-08-24
- **Decider:** Franco Valois Delucca
- **Skill:** `~/bin/dsh-dispatch` (v2, tar-streaming via Proxmox bastion)

## Context

StepLemon is a single-developer Android project. Some tickets (multi-file
refactors, codebase audits, debugging across many files) are too large for
one chat turn but too small to interrupt a human. DSH is a remote DeepSeek
agent running on the Proxmox LXC at `dsh.homelab:3080` (CT 200) that can
run these tasks in a separate bash session, persisting state across tool
calls.

This ADR captures the operational lessons from the first real DSH dispatch
(ticket 11 / PR #6, the 4-layer-bug fix).

## Decision

**Use `~/bin/dsh-dispatch` for multi-file coding work** that meets the
"decision template" criteria in the `deepseek-harness-dispatch` skill:

1. Remote DSH is up (`curl http://192.168.3.122:3080/` returns 200).
2. Task is coding or shell work with multiple steps.
3. Workspace is a git repo or scratch dir.
4. Real agent loop adds value over one search/read.
5. Task can be expressed in 1–3 sentences with a clear desired outcome.

## Workflow (the loop)

```
┌─────────────────┐     1. open ticket       ┌──────────────┐
│  Hermes (chat)  │ ───────────────────────▶│  GitHub PR/  │
│  (this session)  │                            │  ticket/     │
└────────┬────────┘                            │  issue       │
         │                                       └──────┬───────┘
         │ 2. dispatch via ~/bin/dsh-dispatch              │
         ▼                                                  │
┌─────────────────┐     3. write code           ┌──────────────┐
│ DSH on LXC 200  │ ────────────────────────▶ │  GitHub PR   │
│ (49 tool calls, │   opens PR (or commit +   │  (squash)    │
│  3651 events)   │    push branch)            │              │
└────────┬────────┘                            └──────┬───────┘
         │ 4. final_response                       5. CI green
         ▼                                            ▼
┌─────────────────┐                            ┌──────────────┐
│  Hermes merges  │ ─────────────────────────▶│   merged     │
│  + delivers APK │   reports to user         │   on main    │
└─────────────────┘                            └──────────────┘
```

## When the user asks "what did the DSH agent do?"

1. **Tail the session log** on the LXC:
   ```bash
   ssh proxmox "pct exec 200 -- ls /home/node/.dsh/sessions/"
   # Find the session id (folder name = session-<uuid>)
   ssh proxmox "pct exec 200 -- bash -lc 'zstd -d -c /home/node/.dsh/sessions/<sid>/session.jsonl.zstd | tail -10'"
   ```
2. **Pull back the workspace** if a dispatch was killed mid-write. The
   workspace lives at `/workspace/<basename>` on the LXC; the
   `recovery-after-timeout` recipe in the `deepseek-harness-dispatch`
   skill has the full commands.

## Pitfalls (first-dispatch lessons)

### Force-push to `main` blew away local main

DSH's dispatch ran `git push origin HEAD:main --force` because the
task description told it to "push" without specifying a branch. The
result: my local main got reset to a single "Initial commit: snapshot
of upstream main HEAD" commit, and the actual main on GitHub was also
overwritten (or at least my view of it was — see below).

**Mitigation going forward:**
- Task descriptions **must** specify the exact branch name, e.g.
  `Apply the fix on a NEW branch fix/v3.3-stats-really-take2 from main`.
- If the dispatch has finished and you suspect a force-push,
  `git reflog` to recover the lost commit (still in local objects).
- `git log origin/main --oneline | wc -l` to see if remote main is
  shorter than expected.

### DSH's "Initial commit: snapshot of upstream main HEAD"

This was a single squashed commit with message "Initial commit:
snapshot of upstream main HEAD (d7fb0bf2)" that DSH created as the
base of its new branch. The SHA `d7fb0bf2` is the real `main` HEAD
on origin. So DSH was being honest — but the `git push origin
fix/v3.3-stats-really-take2` should not have been `git push origin
HEAD:main --force`.

### Cherry-pick + force-push to recover

To get DSH's commit onto a real main branch:

```bash
git checkout main
git branch -D fix/v3.3-stats-really-take2  # delete the squashed broken version
git checkout -b fix/v3.3-stats-really-take2 origin/main
git cherry-pick <dsh-commit-sha>
git push -u origin fix/v3.3-stats-really-take2 --force-with-lease
gh pr create --base main --head fix/v3.3-stats-really-take2
```

## Cost profile (first dispatch)

- **Time:** 22 min 34 s (1354 s)
- **Tool calls:** 49
- **Events:** 3651
- **Files changed:** 3 (StepRepository +125/-72, StatsFragment +19/-8,
  HistoryFragment +9/-0)
- **APK built:** 5.96 MB
- **Outcome:** correct, merged in PR #6, fix verified by user-installed
  CI artifact.

## Boundaries

**Do dispatch when:**
- Multi-file refactor (4+ files)
- Cross-cutting bug with multiple interacting layers
- Codebase audit ("find all places that import X")
- Heavy gradle / Android build work (offloaded to the LXC's 4c/3GB)

**Do NOT dispatch when:**
- Single-file change
- One-shot question
- Hermes-specific tools needed (memory, session_search, kanban)
- Task involves external web research (DSH on the LXC may not have
  web — verify with `ssh dsh "curl https://api.github.com/zen"` first)

## Related

- `deepseek-harness-dispatch` skill — the full dispatcher reference.
- `agents.toml [invariants.must]` — DSH respects hard rules (no Compose,
  no chart libs, no Foreground Service, etc.).
- `decisions/0005-mockito-for-tests.md` — one of the tests the DSH
  agent had to navigate around (MatrixCursor null issue).