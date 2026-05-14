---
name: check-code-attribution
description: Check vendored code attributions in branch diff and flag any that are deficient. Use when asked to "check attribution", "check licenses", "verify vendored code attribution", or "check code attribution".
allowed-tools: Bash, Read, Grep, Glob
---

# Check Code Attribution

Verify that vendored/adapted third-party code in the current branch diff has correct license headers and `THIRD_PARTY_NOTICES.md` entries.

## Step 1: Run the Candidate Detection Script

Run the pre-filter script:

```bash
bash .claude/skills/check-code-attribution/find-attribution-candidates.sh
```

If the script exits with code 0, there are no candidates. Print "✅ No attribution issues found."

If the script exits with any code besides 0 or 10, it failed (e.g., could not determine merge-base). Print the stderr output and **stop**.

If the script exits with code 10, it outputs:

1. **Global metadata** (first two lines):
   ```
   notices_file_exists: true|false
   notices_file_changed: true|false
   ```

2. **Candidate blocks** (one per flagged file):
   ```
   ---
   file: <path>
   status: A|M|D|R (i.e., "added", "modified", "deleted", "renamed")
   reasons: <comma-separated list of why this file was flagged>
   ---
   ```

The script handles candidate identification deterministically — including committed, staged, and unstaged changes — so trust its output. Do not dismiss a candidate as a false positive based on the committed diff alone.

Parse the output and proceed to Step 2.

## Step 2: Gather Context

Read `CODE_ATTRIBUTION_CRITERIA.md` (in this skill's directory) for the canonical attribution format.

If `notices_file_exists` is `true`, read `THIRD_PARTY_NOTICES.md` to understand existing entries.

## Step 3: Analyze Each Candidate

**Skip analysis for deleted files** (`status: D`) — they only need a 👀 verify finding.

For each non-deleted candidate:

1. **Read the file** and check for the required attribution fields from `CODE_ATTRIBUTION_CRITERIA.md`. Extra information beyond the required fields is fine, as are differences in formatting. Only flag **missing** fields.

2. **Match to a `THIRD_PARTY_NOTICES.md` entry** — Try to find a corresponding entry by URL, library name, copyright holder, or other context. Record whether you found a match, and if so, which entry.

3. **Check the license type** — Identify the license declared in the file's header. Check whether this license type is already represented in `THIRD_PARTY_NOTICES.md` headings. If it's a new license type not yet in NOTICES, flag it for compatibility review.

## Step 4: Check for NOTICES Entry Changes

If `notices_file_changed` is `true`, retrieve the merge-base version of `THIRD_PARTY_NOTICES.md` and compare it against the current version. Skip entries whose source files were already analyzed as candidates in Step 3.

1. **Removed entries** — Headings present in the old version but absent in the new. Check whether the source files referenced in the entry's Scope section still exist and contain third-party attribution headers. If so, flag it. If the referenced files were also deleted in this branch, note it as informational only.

2. **Modified entries** — Headings present in both but with changed content. Compare the entry's metadata (Source, License, Copyright) against the source file headers. Flag inconsistencies; note consistent changes as informational only.

## Step 5: Output Results

If there are no issues, print:

```
✅ No attribution issues found.
```

Otherwise, print findings as a numbered list. Use fully qualified class names (e.g., `io.sentry.cache.tape.FileObjectQueue`). Guidelines:

- **⚠️** = must fix before merging (missing fields, stripped attribution, inconsistent or orphaned NOTICES entries).
- **👀** = author should verify (deleted/renamed files, matched NOTICES entries, consistent NOTICES modifications).
- Keep license-header issues and `THIRD_PARTY_NOTICES.md` issues in separate bullets.
- For license types not yet in NOTICES, link https://open.sentry.io/licensing/ for compatibility review.
- Be concise — say what's wrong and what to do.
- If any candidates are false positives, list them at the end with a one-line reason each.

Example output:

```
Code Attribution Check
══════════════════════

Urgent
────────────
1. ⚠️ io.sentry.util.TokenBucket
   Vendored code (Guava) — header is missing the source URL and copyright year.
   - No corresponding `THIRD_PARTY_NOTICES.md` entry; add one.

Verify
──────
2. 👀 io.sentry.cache.tape.FileObjectQueue
   Vendored code (Square Tape) — verify `THIRD_PARTY_NOTICES.md` reflects your updates.

False positives
───────────────
1. AGENTS.md — project documentation, not vendored code.
```
