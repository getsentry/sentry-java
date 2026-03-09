---
name: create-java-pr
description: Create a pull request in sentry-java. Use when asked to "create pr", "prepare pr", "prep pr", "open pr", "ready for pr", "prepare for review", "finalize changes". Handles branch creation, code formatting, API dump, committing, pushing, PR creation, changelog, and stacked PRs.
---

# Create Pull Request (sentry-java)

Prepare local changes and create a pull request for the sentry-java repo.

**Required reading:** Before proceeding, read `.cursor/rules/pr.mdc` for the full PR and stacked PR workflow details. That file is the source of truth for PR conventions, stack comment format, branch naming, and merge strategy.

## Step 0: Determine PR Type

Ask the user (or infer from context) whether this is:

- **Standalone PR** — a regular PR targeting `main`. Follow Steps 1–6 as written.
- **First PR of a new stack** — ask for a topic name (e.g. "Global Attributes"). Create a collection branch from `main`, then branch the first PR off it. The first PR targets the collection branch.
- **Next PR in an existing stack** — identify the previous stack branch and topic. This PR targets the previous stack branch.

If the user mentions "stack", "stacked PR", or provides a topic name with a number (e.g. `[Topic 2]`), treat it as a stacked PR. See `.cursor/rules/pr.mdc` § "Stacked PRs" for full details.

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

Then continue to Step 5.5 (stacked PRs only) or Step 6.

## Step 5.5: Update Stack List on All PRs (stacked PRs only)

Skip this step for standalone PRs.

After creating the PR, update the PR description on **every other PR in the stack — including the collection branch PR** — so all PRs have the same up-to-date stack list. Follow the format and commands in `.cursor/rules/pr.mdc` § "Stack List in PR Description".

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

```bash
gh pr view <PR_NUMBER> --json body --jq '.body' > /tmp/pr-body.md
printf '\n#skip-changelog\n' >> /tmp/pr-body.md
gh pr edit <PR_NUMBER> --body-file /tmp/pr-body.md
```
