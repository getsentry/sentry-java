# Stacked PRs

Stacked PRs split a large feature into small, easy-to-review PRs where each builds on the previous
one. The general mechanics are the standard [Graphite](https://graphite.dev/) stacking workflow —
this file covers only what is specific to sentry-java.

## Why a Collection Branch

```
main ← collection-branch ← stack-pr-1 ← stack-pr-2 ← stack-pr-3 ← ...
```

A **collection branch** is created from `main` and targets `main`. The first stack PR targets it
rather than `main`, and each later PR targets the previous stack PR's branch.

It exists because PRs targeting `main` are **squash**-merged, which causes repeated merge conflicts
when syncing a stack. Stack PRs are therefore **merge-committed** into the collection branch, and
only the collection branch is squash-merged into `main` at the end — giving `main` one clean commit
for the whole feature.

Create it with an empty commit, so GitHub allows opening a PR:

```bash
git commit --allow-empty -m "collection: <topic>"
```

Push it and open its PR against `main` right away — it is the PR the whole stack is eventually
squash-merged through, and it carries the stack list like every other PR. Give it a plain title
(`<type>(<scope>): <Topic>`, no `[<Topic> <N>]` bracket) and no merge method reminder.

## Rules That Will Destroy a Stack If Broken

**Never update the collection branch yourself.** Never merge, fast-forward, or push stack branch
commits into it. It stays at its initial position (the empty commit on `main`) until the user merges
stack PRs through GitHub one by one. Fast-forwarding it makes GitHub auto-merge and delete every
stack PR branch, destroying the entire stack.

**Never amend or force-push a stack branch.** No `git commit --amend`, `--force`, or
`--force-with-lease` on a branch that is part of a stack — a force-push can cause GitHub to
auto-merge or auto-close the other PRs in the stack. If a commit needs fixing, add a fixup commit.

**Sync only between adjacent stack branches**, by merging forward — never into the collection branch.
Prefer merge over rebase; only rebase if explicitly requested.

**Do not merge PRs.** Only the user merges them, bottom to top.

## PR Title Naming

Include the topic name and a sequential number in brackets:

```
<type>(<scope>): [<Topic> <N>] <Subject>
```

Examples:
- `feat(core): [Global Attributes 1] Add scope-level attributes API`
- `feat(core): [Global Attributes 2] Wire scope attributes into LoggerApi and MetricsApi`

## Stack List in PR Description

Every PR in the stack — **including the collection branch PR** — must have a stack list **at the top
of its description**, before the `## :scroll: Description` section. When a PR is added, update the
description on **all** PRs in the stack. The stack list is also how you enumerate a stack: read it
off any PR body rather than guessing from branch names, which may use different prefixes.

```markdown
## PR Stack (<Topic>)

- #5118
- #5120
- #5121

---
```

No status column — GitHub already shows that. The `---` separates the stack list from the rest of
the description.

**Merge method reminder:** on stack PRs (not the collection branch PR), end the description with:

```markdown
> ⚠️ **Merge this PR using a merge commit** (not squash). Only the collection branch is squash-merged into main.
```

Updating every PR's stack list means editing several descriptions — follow the procedure in
`SKILL.md` § "Editing PR Descriptions".
