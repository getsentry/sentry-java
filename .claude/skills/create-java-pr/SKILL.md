---
name: create-java-pr
description: Create a pull request in sentry-java. Use when asked to "create pr", "prepare pr", "prep pr", "open pr", "ready for pr", "prepare for review", "finalize changes". Handles branch creation, code formatting, API dump, committing, pushing, PR creation, changelog, and stacked PRs.
---

# Create Pull Request (sentry-java)

Prepare local changes and create a pull request for the sentry-java repo.

**Required reading:** Read `.cursor/rules/pr.mdc` before proceeding. It is the source of
truth for every convention referenced below — branch naming, commit format, PR body
template, changelog subsections, stack list format, and merge strategy. This skill is the
procedure; `pr.mdc` is the reference.

## Step 0: Determine PR Type From Git Branch Context

Infer PR type from the current branch before asking the user.

1. Get current branch:

```bash
git branch --show-current
```

2. Apply these rules:

- **If branch is `main` or `master`**: default to a **standalone PR**.
  - Do **not** assume stack mode from `main`.
  - Only use stack mode if the user explicitly asks for a stacked PR.
- **If branch is not `main`/`master`**:
  - Check whether that branch already has a PR and what its base is:
    ```bash
    gh pr list --head "$(git branch --show-current)" --json number,baseRefName,title --jq '.[0]'
    ```
  - If that branch PR exists and `baseRefName` is **not** `main`/`master`, treat the work as a **stacked PR context**.
  - If that branch PR exists and `baseRefName` **is** `main`/`master`, also check whether other PRs target the current branch:
    ```bash
    gh pr list --base "$(git branch --show-current)" --json number,headRefName,title
    ```
    - If there are downstream PRs, treat this as **next PR in an existing stack** with the current branch as the stack base (collection branch).
    - If there are no downstream PRs, treat it as **standalone PR context**.
  - If no PR exists for the current branch, check whether other PRs target it:
    ```bash
    gh pr list --base "$(git branch --show-current)" --json number,headRefName,title
    ```
    - If there are downstream PRs, treat this as **next PR in an existing stack** with the current branch as the stack base (collection branch).
    - If there are no downstream PRs either, treat it as **standalone PR context** (fresh feature branch).

3. If signals are mixed or ambiguous, ask one focused question to confirm.

PR types:
- **Standalone PR** — regular PR targeting `main`.
- **First PR of a new stack** — create collection branch from `main`, then first PR off it.
- **Next PR in an existing stack** — target the current stack base branch (usually the previous stack PR branch, or the collection branch if creating the first follow-up PR from the collection branch).

If the user explicitly says "stack", "stacked PR", or provides numbered stack titles (e.g. `[Topic 2]`), honor that even if branch heuristics are inconclusive.

## Step 1: Ensure Feature Branch

If on `main` or `master`, create and switch to a new branch, deriving the name from the
changes being made. See `pr.mdc` § "Step 1: Ensure Feature Branch" for naming.

**For stacked PRs:** For the first PR in a new stack, create and push the collection branch
first (`pr.mdc` § "Creating the Collection Branch"), then branch the PR off it. For
subsequent PRs, branch off the previous stack branch. Naming: `pr.mdc` § "Branch Naming".

**CRITICAL: Never merge, fast-forward, or push commits into the collection branch.** It
stays at its initial position until the user merges stack PRs through GitHub. Updating it
will auto-merge and destroy the entire PR stack.

## Step 2: Format Code and Regenerate API Files

```bash
./gradlew spotlessApply apiDump
```

Required before every PR in this repo. If it fails, diagnose and fix before continuing.

## Step 3: Commit Changes

Check for uncommitted changes:

```bash
git status --porcelain
```

If there are uncommitted changes, invoke the `sentry-skills:commit` skill to stage and
commit them following Sentry conventions.

**Important:** Leave out changes that are only relevant for local testing — hardcoded debug
toggles, sample app config, `.env` or credentials files. Restore them before committing:

```bash
git checkout -- <file-to-restore>
```

## Step 4: Push the Branch

```bash
git push -u origin HEAD
```

If the push fails due to diverged history, ask the user how to proceed rather than
force-pushing.

## Step 5: Create PR

Invoke the `sentry-skills:create-pr` skill to create a draft PR. Use the repo's PR template
at `.github/pull_request_template.md` for the body, filling in each section based on the
changes and checking any checklist items that apply.

**For stacked PRs**, additionally:

- Pass `--base <previous-stack-branch>` (the collection branch for the first PR in a stack).
- Use the stacked PR title format from `pr.mdc` § "PR Title Naming".
- Add the stack list at the top of the body, before `## :scroll: Description`
  (`pr.mdc` § "Stack List in PR Description").
- Add the merge method reminder at the very end of the body (same section). Stack PRs only,
  not the collection branch PR.

Then continue to Step 5.5 (stacked PRs only) or Step 6.

## Step 5.5: Update Stack List on All PRs (stacked PRs only)

Skip for standalone PRs.

After creating the PR, update the description on **every other PR in the stack — including
the collection branch PR** so they all carry the same up-to-date stack list. Format and
commands: `pr.mdc` § "Stack List in PR Description".

## Step 6: Update Changelog

First decide whether an entry is needed. **Skip to "No changelog needed"** if the changes
are not user-facing, for example:

- Test-only changes (new tests, test refactors, test fixtures)
- CI/CD or build configuration changes
- Documentation-only changes
- Code comments or formatting-only changes
- Internal refactors with no behavior change visible to SDK users
- Sample app changes

If unsure, ask the user.

### If changelog is needed

Add an entry to `CHANGELOG.md` under `## Unreleased`, using the subsection table and entry
format in `pr.mdc` § "Step 6: Update Changelog". Use the PR number returned by
`sentry-skills:create-pr`, and match the style of surrounding entries.

Then stage, commit with the message `changelog`, and push.

### No changelog needed

Add `#skip-changelog` to the PR description to disable the changelog CI check:

1. Get the current body: `gh pr view <PR_NUMBER> --json body --jq '.body'`
2. Use the `Write` tool to save the output to `/tmp/pr-body.md`, appending `\n#skip-changelog\n` at the end
3. Update: `gh pr edit <PR_NUMBER> --body-file /tmp/pr-body.md`

**Note:** When updating PR bodies, never use shell redirects (`>`, `>>`), pipes (`|`), or
compound commands (`&&`). These form compound shell expressions that won't match permission
patterns. Use `gh pr view --json body --jq '.body'` to read, the `Write`/`Edit` tools to
modify a temp file, and `gh pr edit --body-file` to write back.
