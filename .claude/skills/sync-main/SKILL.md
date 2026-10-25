---
name: sync-main
description: Update the fork's main branch from upstream/main. Merges upstream/main into every pending branch (origin branches with an open PR against upstream), builds a fresh sync/main from origin/main with upstream/main and all pending branches merged in, and opens a PR into origin's main. Conflicts go through the resolve-conflicts skill. Use when the user asks to sync main, update from upstream, or refresh pending branches.
disable-model-invocation: true
allowed-tools: Bash(git *), Bash(gh *), Bash(skip *), Bash(swift build*), Bash(rm -rf .build), Skill, AskUserQuestion
---

<!-- Shared copy: keep identical across forks (skip-ui, skip-fuse-ui). Repo-agnostic. -->

# Sync origin/main from upstream/main

Terms:
- **Pending branches**: branches in `origin` that have an open PR against `upstream`. Origin branches without an upstream PR are ignored (mention them, never touch them).
- `sync/main`: a throwaway branch rebuilt from `origin/main` on every run.

Rules that always apply:
- Every conflict goes through the `resolve-conflicts` skill. Never resolve one yourself.
- Never discard user work. Stop and ask when anything is ambiguous.
- A `--force-with-lease` push is allowed only to `sync/main`, which this skill recreates by design.
- Always pass `-R <owner/repo>` to every `gh` command. With an `upstream` remote present, bare `gh` may target the wrong repository.

## 1. Preflight

```sh
cd "$(git rev-parse --show-toplevel)"     # stop if this fails
git remote -v
```

- `upstream` must be the parent and `origin` a fork of it: `gh repo view <origin-owner>/<repo> --json nameWithOwner,isFork,parent,defaultBranchRef`. `isFork` must be true and `parent` must match `upstream`. If `upstream` is missing but `origin` is a fork, offer to add it (match origin's URL protocol). Otherwise report the mismatch and stop.
- `git fetch upstream` and `git fetch origin --prune`. Note origin's default branch as DEFAULT (expected `main`).
- Working tree must be clean for merges. If `git status --porcelain` shows lines not starting with `??`, stash with `git stash push -m "sync-<timestamp>"` and restore at the end. Record the starting branch.

## 2. Find the pending branches

```sh
gh pr list -R <upstream-owner>/<repo> --state open --limit 500 \
  --json number,title,headRefName,headRepositoryOwner,isDraft \
  -q '.[] | select(.headRepositoryOwner.login=="<origin-owner>") | [.number,.headRefName,.isDraft,.title] | @tsv'
```

Missing a branch is the worst failure of this skill, so:
1. If the result hits `--limit`, raise it and rerun.
2. Cross-check with `git for-each-ref refs/remotes/origin --format='%(refname:short)'`. List every origin branch not in the PR list as "no upstream PR, not updated".
3. Include drafts and flag them.
4. Show a table: PR number, branch, draft, commits behind `upstream/main` (`git rev-list --count origin/<b>..upstream/main`), commits ahead, and whether `upstream/main` is already an ancestor (`git merge-base --is-ancestor upstream/main origin/<b>`).
5. Exclude `sync/main`, `sync/*`, and DEFAULT. Ask the user to confirm the list before changing anything. If empty, say so and stop.

## 3. Update each pending branch from upstream/main

For each branch, one at a time:

```sh
git checkout <branch>
git merge --ff-only origin/<branch>
git merge upstream/main --no-edit
```

- Already contains `upstream/main`: record `up-to-date`.
- Clean merge: verify (below), `git push origin <branch>`, record `updated`.
- Conflict: leave the merge in progress and invoke the `resolve-conflicts` skill. It resolves with the user and commits. Then verify, push, record `updated`. If the user skips, `git merge --abort` and record `skipped`.

Finish the clean branches first, then bring conflicted ones to the user one at a time. Never continue a loop after a failed `git merge --abort`; check `git status` first. On `index.lock` errors wait and retry.

**Verify** before each push (and at the later steps): run `swift build` and, if Android tooling is available, `skip test` (`export ANDROID_HOME=~/Library/Android/sdk` if unset). Report counts and failures honestly, and say if a check cannot run. If the disk is full, ask before deleting anything except the repo's own `.build`.

Gate: print a table (branch, PR, status, verification). Confirm with the user that every non-skipped branch now contains `upstream/main` (`git merge-base --is-ancestor upstream/main origin/<b>`) before continuing.

## 4. Recreate sync/main from origin/main

```sh
git checkout <DEFAULT>-or-any-other-branch      # leave sync/main if checked out
git branch -D sync/main 2>/dev/null
git checkout -b sync/main origin/<DEFAULT>
git merge upstream/main --no-edit
```

On conflicts, invoke `resolve-conflicts`. Expect them: this is where the pending branches that upstream has not merged yet collide with others' upstream work. Verify afterwards.

## 5. Merge the pending branches into sync/main

For each pending branch from step 2 (skipped ones excluded), in listed order: if it is already an ancestor of `sync/main`, skip it, otherwise `git merge origin/<branch> --no-edit`.

These should merge cleanly, because each branch now contains `upstream/main`. A conflict here is unexpected. Tell the user it is, and why it likely happened (a branch differs from what `origin/main` already holds, or two pending branches overlap), then invoke `resolve-conflicts`. Verify at the end.

## 6. Push and open or update the PR

```sh
git push --force-with-lease origin sync/main      # plain push if it did not exist on origin
```

Then `gh pr list -R <origin-owner>/<repo> --head sync/main --state open`.
- None: `gh pr create -R <origin-owner>/<repo> --base <DEFAULT> --head sync/main --title "Sync upstream/main → <DEFAULT> (<date>)" --body <body>`.
- One exists: `gh pr edit` its body and report the URL.

Body: incoming upstream commits summary; merged pending branches with upstream PR numbers (`- <upstream-owner>/<repo>#<n> <branch>`); conflict groups with the strategy chosen and why; verification results (build, tests, what could not run); skipped branches. Be honest about what was not verified. Follow session attribution instructions for PR bodies.

## 7. Finish

Restore any stash, return to the starting branch, delete build artifacts you created if disk space matters, and report: PR URL, the per-branch status table, verification results, and anything skipped or needing follow-up.
