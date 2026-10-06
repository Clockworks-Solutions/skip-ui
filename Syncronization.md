# Upstream Sync Guide

Keep our forks (`skip-ui`, `skip-fuse-ui`) up to date with upstream (`skiptools`), then carry the changes into `liquid-glass`.

## At a glance

```
 ┌──────────────────────────────────────────────────────────────┐
 │ 1. PENDING PR BRANCHES                                       │
 │                                                              │
 │    upstream/main ──merge──► each open PR branch              │
 └──────────────────────────────┬───────────────────────────────┘
                                ▼
 ┌──────────────────────────────────────────────────────────────┐
 │ 2. FORK MAIN                                                 │
 │                                                              │
 │    main ──branch──► sync/upstream             			    │
 │                         ▲       ▲                       		│
 │       pending branches ─┘       └─ upstream/main        		│
 │                                                              │
 │    sync/skiptools-main-<date> ──PR──► main                   │
 └──────────────────────────────┬───────────────────────────────┘
								|
                                ▼  (after the PR is merged)
 ┌──────────────────────────────────────────────────────────────┐
 │ 3. LIQUID-GLASS                                              │
 │                                                              │
 │    liquid-glass ──branch──► sync/main-to-liquid-glass-<date> │
 │                                  ▲                           │
 │                           main ──┘                           │
 │                                                              │
 │    sync/main-to-liquid-glass-<date> ──PR──► liquid-glass     │
 └──────────────────────────────────────────────────────────────┘
```

---

## Step 1: Update pending PR branches

Pending branches are our branches with an open PR to `skiptools`.

- [ ] Check `skiptools/PR's` for our open PRs.
- [ ] **None open?** Go to Step 2.
- [ ] **Some open?** For each of those branches:
  - [ ] Merge `upstream/main` into it.
  - [ ] Resolve conflicts, then build and test.
  - [ ] Push. The open upstream PR updates itself.

## Step 2: Sync fork `main`

- [ ] Create `sync/upstream` from the latest `main`.
- [ ] Merge each pending branch from Step 1 into it.
- [ ] Merge `upstream/upstream-to-main` into it.
- [ ] Resolve conflicts, then build and test.
- [ ] Open a PR: `sync/upstream-to-main` → **our fork's** `main`.
      ⚠️ GitHub defaults the base repo to `skiptools`, so change it to `Clockworks-Solutions`.
- [ ] Merge the PR with **Create a merge commit**, not squash or rebase.

## Step 3: Sync `liquid-glass`

Start only after the Step 2 PR is merged.

- [ ] Create `sync/main-to-liquid-glass` from the latest `liquid-glass`.
- [ ] Merge `main` into it.
- [ ] Resolve conflicts, then build and test.
- [ ] Open a PR: `sync/main-to-liquid-glass` → `liquid-glass` (base repo `Clockworks-Solutions`).
- [ ] Merge the PR with **Create a merge commit**.

---

## Remember

| Rule | Why |
|---|---|
| Always pull the latest branches first | Merging old branches brings back conflicts that are already fixed |
| Only use merge commits on sync PRs | Set the PR base repo to `Clockworks-Solutions` 
| On a fork, GitHub targets upstream by default |
| Do Step 3 only after the Step 2 PR is merged | `liquid-glass` must take the merged `main` |
| Repeat everything for this repo | `skip-ui` first, then `skip-fuse-ui` |
| Delete sync branches once their PRs are merged | Keeps the branch list clean |
