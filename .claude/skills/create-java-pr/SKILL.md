---
name: create-java-pr
description: Create a pull request in sentry-java. Use when asked to "create pr", "prepare pr", "prep pr", "open pr", "ready for pr", "prepare for review", "finalize changes". Handles branch creation, code formatting, API dump, committing, pushing, PR creation, changelog, and stacked PRs.
---

# Create Pull Request (sentry-java)

Prepare local changes and create a pull request for the sentry-java repo.

**Required reading:** Before proceeding, read `.cursor/rules/pr.mdc` for the full PR and stacked PR workflow details. That file is the source of truth for PR conventions, stack comment format, branch naming, and merge strategy.

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
    If there are downstream PRs, treat this as **next PR in an existing stack** with the current branch as the stack base (collection branch).

3. If signals are mixed or ambiguous, ask one focused question to confirm.

PR types:
- **Standalone PR** — regular PR targeting `main`.
- **First PR of a new stack** — create collection branch from `main`, then first PR off it.
- **Next PR in an existing stack** — target the current stack base branch (usually the previous stack PR branch, or the collection branch if creating the first follow-up PR from the collection branch).

If the user explicitly says "stack", "stacked PR", or provides numbered stack titles (e.g. `[Topic 2]`), honor that even if branch heuristics are inconclusive.

## Step 1: Ensure Feature Branch

```bash
git branch --show-current
```

If on `main` or `master`, create and switch to a new branch:

```bash
git checkout -b <type>/<short-description>
```

Derive the branch name from the changes being made. Use `feat/`, `fix/`, `ref/`, etc. matching the commit type conventions.

**For stacked PRs:** For the first PR in a new stack, first create and push the collection branch (see `.cursor/rules/pr.mdc` § "Creating the Collection Branch"), then branch the PR off it. For subsequent PRs, branch off the previous stack branch. Use the naming conventions from `.cursor/rules/pr.mdc` § "Branch Naming".

**CRITICAL: Never merge, fast-forward, or push commits into the collection branch.** It stays at its initial position until the user merges stack PRs through GitHub. Updating it will auto-merge and destroy the entire PR stack.

## Step 2: Format Code and Regenerate API Files

```bash
./gradlew spotlessApply apiDump
```

This is **required** before every PR in this repo. It formats all Java/Kotlin code via Spotless and regenerates the `.api` binary compatibility files.

If the command fails, diagnose and fix the issue before continuing.

## Step 3: Commit Changes

Check for uncommitted changes:

```bash
git status --porcelain
```

If there are uncommitted changes, invoke the `sentry-skills:commit` skill to stage and commit them following Sentry conventions.

**Important:** When staging, ignore changes that are only relevant for local testing and should not be part of the PR. Common examples:

| Ignore Pattern | Reason |
|---|---|
| Hardcoded booleans flipped for testing | Local debug toggles |
| Sample app config changes (`sentry-samples/`) | Local testing configuration |
| `.env` or credentials files | Secrets |

Restore these files before committing:

```bash
git checkout -- <file-to-restore>
```

## Step 4: Push the Branch

```bash
git push -u origin HEAD
```

If the push fails due to diverged history, ask the user how to proceed rather than force-pushing.

## Step 5: Create PR

Invoke the `sentry-skills:create-pr` skill to create a draft PR. When providing the PR body, use the repo's PR template structure from `.github/pull_request_template.md`:

```
## :scroll: Description
<Describe the changes in detail>

## :bulb: Motivation and Context
<Why is this change required? What problem does it solve?>

## :green_heart: How did you test it?
<Describe how you tested>

## :pencil: Checklist
- [ ] I added GH Issue ID _&_ Linear ID
- [ ] I added tests to verify the changes.
- [ ] No new PII added or SDK only sends newly added PII if `sendDefaultPII` is enabled.
- [ ] I updated the docs if needed.
- [ ] I updated the wizard if needed.
- [ ] Review from the native team if needed.
- [ ] No breaking change or entry added to the changelog.
- [ ] No breaking change for hybrid SDKs or communicated to hybrid SDKs.

## :crystal_ball: Next steps
```

Fill in each section based on the changes being PR'd. Check any checklist items that apply.

**For stacked PRs:**

- Pass `--base <previous-stack-branch>` so the PR targets the previous branch (first PR in a stack targets the collection branch).
- Use the stacked PR title format: `<type>(<scope>): [<Topic> <N>] <Subject>` (see `.cursor/rules/pr.mdc` § "PR Title Naming").
- Include the stack list at the top of the PR body, before the `## :scroll: Description` section (see `.cursor/rules/pr.mdc` § "Stack List in PR Description" for the format).
- Add a merge method reminder at the very end of the PR body (see `.cursor/rules/pr.mdc` § "Stack List in PR Description" for the exact text). This only applies to stack PRs, not the collection branch PR.

Then continue to Step 5.5 (stacked PRs only) or Step 6.

## Step 5.5: Update Stack List on All PRs (stacked PRs only)

Skip this step for standalone PRs.

After creating the PR, update the PR description on **every other PR in the stack — including the collection branch PR** — so all PRs have the same up-to-date stack list. Follow the format and commands in `.cursor/rules/pr.mdc` § "Stack List in PR Description".

**Important:** When updating PR bodies, never use shell redirects (`>`, `>>`) or pipes (`|`) or compound commands (`&&`). These create compound shell expressions that won't match permission patterns. Instead:
- Use `gh pr view <NUMBER> --json body --jq '.body'` to get the body (output returned directly)
- Use the `Write` tool to save it to a temp file
- Use the `Edit` tool to modify the temp file
- Use `gh pr edit <NUMBER> --body-file /tmp/pr-body.md` to update

## Step 6: Update Changelog

First, determine whether a changelog entry is needed. **Skip this step** (and go straight to "No changelog needed" below) if the changes are not user-facing, for example:

- Test-only changes (new tests, test refactors, test fixtures)
- CI/CD or build configuration changes
- Documentation-only changes
- Code comments or formatting-only changes
- Internal refactors with no behavior change visible to SDK users
- Sample app changes

If unsure, ask the user.

### If changelog is needed

Add an entry to `CHANGELOG.md` under the `## Unreleased` section.

#### Determine the subsection

| Change Type | Subsection |
|---|---|
| New feature | `### Features` |
| Bug fix | `### Fixes` |
| Refactoring, internal cleanup | `### Internal` |
| Dependency update | `### Dependencies` |

Create the subsection under `## Unreleased` if it does not already exist.

#### Entry format

```markdown
- <Short description of the change> ([#<PR_NUMBER>](https://github.com/getsentry/sentry-java/pull/<PR_NUMBER>))
```

Use the PR number returned by `sentry-skills:create-pr`. Match the style of existing entries — sentence case, ending with the PR link, no trailing period.

#### Commit and push

Stage `CHANGELOG.md`, commit with message `changelog`, and push:

```bash
git add CHANGELOG.md
git commit -m "changelog"
git push
```

### No changelog needed

If no changelog entry is needed, add `#skip-changelog` to the PR description to disable the changelog CI check:

1. Get the current body: `gh pr view <PR_NUMBER> --json body --jq '.body'`
2. Use the `Write` tool to save the output to `/tmp/pr-body.md`, appending `\n#skip-changelog\n` at the end
3. Update: `gh pr edit <PR_NUMBER> --body-file /tmp/pr-body.md`
