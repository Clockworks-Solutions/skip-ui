---
name: resolve-conflicts
description: Resolve an in-progress git merge conflict interactively and commit the result. Researches the intent behind each side using blame and commit/PR history, groups related hunks, asks the user to confirm each group, stages it, and commits once everything is approved. Never auto-resolves. Use when a `git merge` has stopped with conflicts.
allowed-tools: Bash(git *), Bash(gh *), Bash(grep *), Bash(swift build*), Bash(skip *), AskUserQuestion
---

<!-- Shared copy: keep identical across forks (skip-ui, skip-fuse-ui). Repo-agnostic. -->

# Resolve merge conflicts

Run while a merge is in progress (`MERGE_HEAD` exists). Resolves the conflicts one intent-group at a time with user confirmation, then commits. It does not push, and it does not decide which branches to merge; the calling workflow does that.

Rules that always apply:
- Never auto-resolve or resolve silently, including conflicts that `git rerere` replays.
- Never discard user work. Stop and ask when anything is ambiguous.
- Never force-push.

## 1. Preconditions

- `git rev-parse -q --verify MERGE_HEAD` must succeed, and `git diff --name-only --diff-filter=U` must list files. Otherwise say so and stop.
- Make sure rerere is on for this repo: `git config rerere.enabled true` and `git config rerere.autoUpdate false`.
- If `git rerere status` / `git rerere diff` show recorded resolutions being replayed, keep them as *proposals*. They are shown to the user in step 4 labelled "previously resolved", and need the same confirmation.

## 2. Research the intent of each side

For each conflicted file:

```sh
B=$(git merge-base HEAD MERGE_HEAD)
git log --format='%h %an %ad %s' --date=short $B..HEAD       -- <file>   # our side
git log --format='%h %an %ad %s' --date=short $B..MERGE_HEAD -- <file>   # their side
git diff --stat $B HEAD -- <file>
git diff --stat $B MERGE_HEAD -- <file>
git diff -w --stat HEAD MERGE_HEAD -- <file>      # mostly whitespace?
```

For each conflict hunk, find which commits introduced each side's lines:

```sh
git blame -L <start>,<end> --date=short <ours-rev> -- <file>
git log -L <start>,<end>:<file> --format='%h %ad %s' -n 3 <rev>
```

Read the commit messages, and look up PRs when a number appears (`gh pr view <n> -R <owner/repo>`). For each side write down its **intent**: what the commit or PR was trying to achieve, not just what text changed. Also answer: which side is older, did one side supersede the other, is the difference only whitespace or line endings.

Watch for:
- **Whole-file hunks**: usually indentation or line-ending changes, or a branch built on a pre-refactor base. Check `git diff -w` and `--ignore-cr-at-eol` and say so.
- **Superseded code**: one side's lines exist only in a commit that the other side later replaced.
- **Both sides add different things** at the same spot: usually keep both.
- **Partial-conflict leftovers**: code outside conflict markers can belong to the old version. Compare the whole file with `git diff -w <base> -- <file>` if braces or symbols look off.

## 3. Group hunks by intent

Cluster hunks, across files, that belong to the same originating commit or PR or form one logical change (a rename plus its call sites, a refactor plus the code adapted to it). Each cluster is one decision unit. A hunk with no relatives is its own group. Order groups so that foundational changes (types, signatures) come before their users.

## 4. Present and confirm one group at a time

For each group show:
1. Files and locations (`file:line`) and a one-line description of what the code does.
2. **Intent of ours** and **intent of theirs**, with commit hashes, dates, subjects and PR numbers.
3. Ours and theirs trimmed to the differing lines with context (and the rerere proposal if there is one, labelled "previously resolved").
4. A recommendation tied to that history.
5. Options, with one marked "(Recommended)":
   - **A. Take ours**: say exactly what is dropped.
   - **B. Take theirs**: say exactly what is dropped.
   - **C. Keep both / combine**: show the concrete combined code.
   - **D. Take theirs as base and re-apply ours**: list the specific changes to re-apply.
   - **E. Let me edit manually**: wait for the user to confirm.

Ask with AskUserQuestion, one question per group (up to four at a time). For heavily conflicted files, first offer a file-level strategy with the evidence, then go per group only where needed. Trivially repeated patterns can be one group, but show one full example.

## 5. Apply and stage each confirmed group

After the user answers a group: apply exactly that resolution, then `git add` the affected files once all their groups are done. Check that no markers remain (`grep -rn '^<<<<<<<\|^>>>>>>>' <files>`) and that braces balance. For heavily conflicted files, prefer taking a whole known-good file and re-applying small changes, then confirm with `git diff -w <base> -- <file>` that the result differs only by the intended changes. Show the user the resulting diff for the group, then move on to the next group.

## 6. Verify and commit

When every group is resolved:
- Run `swift build`, and `skip test` if Android tooling is available (`export ANDROID_HOME=~/Library/Android/sdk` if unset). Report test counts and failures honestly, and say so if a check cannot run.
- Show a final summary: each group, files, strategy chosen and why. Ask for confirmation to commit.
- `git commit` with a message that lists the conflicted files and the strategy per group. Follow any commit attribution instructions from the current session.

If the user wants to give up, run `git merge --abort` and check `git status` before continuing. If a command fails with an `index.lock` error, wait a few seconds and retry (Xcode may hold it).

Return control to the caller, who pushes.
