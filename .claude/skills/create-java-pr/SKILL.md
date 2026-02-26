---
name: create-java-pr
description: Create a pull request in sentry-java. Use when asked to "create pr", "prepare pr", "prep pr", "open pr", "ready for pr", "prepare for review", "finalize changes". Handles branch creation, code formatting, API dump, committing, pushing, PR creation, and changelog.
---

# Create Pull Request (sentry-java)

Prepare local changes and create a pull request for the sentry-java repo.

## Step 1: Ensure Feature Branch

```bash
git branch --show-current
```

If on `main` or `master`, create and switch to a new branch:

```bash
git checkout -b <type>/<short-description>
```

Derive the branch name from the changes being made. Use `feat/`, `fix/`, `ref/`, etc. matching the commit type conventions.

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

Invoke the `sentry-skills:create-pr` skill to create a draft PR, then continue to Step 6.

## Step 6: Update Changelog

After the PR is created, add an entry to `CHANGELOG.md` under the `## Unreleased` section.

### Determine the subsection

| Change Type | Subsection |
|---|---|
| New feature | `### Features` |
| Bug fix | `### Fixes` |
| Refactoring, internal cleanup | `### Internal` |
| Dependency update | `### Dependencies` |

Create the subsection under `## Unreleased` if it does not already exist.

### Entry format

```markdown
- <Short description of the change> ([#<PR_NUMBER>](https://github.com/getsentry/sentry-java/pull/<PR_NUMBER>))
```

Use the PR number returned by `sentry-skills:create-pr`. Match the style of existing entries — sentence case, ending with the PR link, no trailing period.

### Commit and push

Stage `CHANGELOG.md`, commit with message `changelog`, and push:

```bash
git add CHANGELOG.md
git commit -m "changelog"
git push
```
