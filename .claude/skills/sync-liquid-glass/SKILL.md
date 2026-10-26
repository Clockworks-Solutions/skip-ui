---
name: sync-liquid-glass
description: Update origin/liquid-glass from origin/main. Builds a fresh sync/liquid-glass from origin/liquid-glass, merges origin/main into it, and opens a PR into liquid-glass. Conflicts go through the resolve-conflicts skill. Use when the user asks to sync or update the liquid-glass branch from main.
disable-model-invocation: true
allowed-tools: Bash(git *), Bash(gh *), Bash(skip *), Bash(swift build*), Bash(rm -rf .build), Skill, AskUserQuestion
---

<!-- Shared copy: keep identical across forks (skip-ui, skip-fuse-ui). Repo-agnostic. -->

# Sync origin/liquid-glass from origin/main

Best run after the `sync-main` PR has been merged, so `origin/main` already contains upstream.

Rules that always apply:
- Every conflict goes through the `resolve-conflicts` skill. Never resolve one yourself.
- A `--force-with-lease` push is allowed only to `sync/liquid-glass`, which this skill recreates by design.
- Always pass `-R <owner/repo>` to every `gh` command.

## 1. Preflight

```sh
cd "$(git rev-parse --show-toplevel)"     # stop if this fails
git fetch origin --prune
```

- Work out `<origin-owner>/<repo>` from `git remote get-url origin`, and confirm with `gh repo view <origin-owner>/<repo> --json nameWithOwner,defaultBranchRef`. Note DEFAULT (expected `main`).
- `origin/liquid-glass` and `origin/<DEFAULT>` must exist. If `liquid-glass` is missing, say so and stop.
- Show how far behind it is: `git rev-list --count origin/liquid-glass..origin/<DEFAULT>`. If `origin/<DEFAULT>` is already an ancestor of `origin/liquid-glass`, say it is up to date and stop.
- Working tree must be clean for merges. If `git status --porcelain` shows lines not starting with `??`, stash with `git stash push -m "sync-<timestamp>"` and restore at the end. Record the starting branch.

## 2. Recreate sync/liquid-glass

```sh
git checkout <any-other-branch>                 # leave sync/liquid-glass if checked out
git branch -D sync/liquid-glass 2>/dev/null
git checkout -b sync/liquid-glass origin/liquid-glass
git merge origin/<DEFAULT> --no-edit
```

On conflicts, leave the merge in progress and invoke the `resolve-conflicts` skill. Replayed rerere resolutions from earlier syncs will be shown for confirmation. On `index.lock` errors wait and retry.

## 3. Verify

Run `swift build` and, if Android tooling is available, `skip test` (`export ANDROID_HOME=~/Library/Android/sdk` if unset). Report counts and failures honestly, and say if a check cannot run.

## 4. Push and open or update the PR

```sh
git push --force-with-lease origin sync/liquid-glass      # plain push if it did not exist on origin
```

Then `gh pr list -R <origin-owner>/<repo> --head sync/liquid-glass --state open`.
- None: `gh pr create -R <origin-owner>/<repo> --base liquid-glass --head sync/liquid-glass --title "Sync <DEFAULT> → liquid-glass (<date>)" --body <body>`.
- One exists: `gh pr edit` its body and report the URL.

Body: incoming commits from `<DEFAULT>`, conflict groups with the strategy chosen and why, verification results (and what could not run). Follow session attribution instructions for PR bodies.

## 5. Finish

Restore any stash, return to the starting branch, delete build artifacts you created if disk space matters, and report the PR URL, verification results, and anything needing follow-up.
