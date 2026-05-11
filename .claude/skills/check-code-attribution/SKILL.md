---
name: check-code-attribution
description: Check vendored code attributions in branch diff and flag any that are deficient. Use when asked to "check attribution", "check licenses", "verify vendored code attribution", or "check code attribution".
allowed-tools: Bash, Read, Grep, Glob
---

# Check Code Attribution

Verify that vendored/adapted third-party code in the current branch diff has correct license headers and `THIRD_PARTY_NOTICES.md` entries.

## Step 1: Run the Analysis Script

Run the pre-filter and analysis script:

```bash
bash .claude/skills/check-code-attribution/find-attribution-candidates.sh
```

If the script exits with code 0, there are no candidates. Print "No attribution issues found."

If the script exits with any other code besides 0 or 10, it failed (e.g., could not determine merge-base). Print the stderr output and **stop** — do not attempt to interpret partial output.

If the script exits with code 10, it outputs structured blocks for two types of candidates:

**Source-file candidates** (changed files with attribution markers):

```
---
candidate_type: source_file
file: <path>
status: A|M|D|R
reasons: <comma-separated list of why this file was flagged>
notices_match: url|none|deleted
notices_entry: <heading from THIRD_PARTY_NOTICES.md, if matched>
new_license_type: true|false
notices_file_exists: true|false
notices_file_changed: true|false
---
```

**NOTICES-entry candidates** (entries removed or modified in `THIRD_PARTY_NOTICES.md`):

```
---
candidate_type: notices_entry
notices_entry: <heading from THIRD_PARTY_NOTICES.md>
notices_change: removed|modified
scope_text_start:
<Scope section text from the old entry>
scope_text_end:
notices_file_exists: true|false
notices_file_changed: true|false
---
```

The script deterministically handles:
- Candidate identification and filtering (attribution markers, third-party copyright/license headers, vendor-like path segments)
- Rename detection (`--find-renames`)
- Removed attribution detection (catches accidental stripping of attribution headers)
- URL-based matching to `THIRD_PARTY_NOTICES.md` entries
- New license type detection
- Deleted file identification
- Detection of whether `THIRD_PARTY_NOTICES.md` exists in the repo
- Tracking whether `THIRD_PARTY_NOTICES.md` itself was modified in the diff
- Detection of removed `THIRD_PARTY_NOTICES.md` entries (headings present in old but absent in new version)
- Detection of modified `THIRD_PARTY_NOTICES.md` entries (entry content changed between old and new version)
- Scope text extraction for removed/modified entries

**Important:** The script checks three layers of changes: (1) committed changes on the branch vs. the merge-base, (2) staged but uncommitted changes, and (3) unstaged working-tree changes. A candidate may not appear in `git diff merge-base..HEAD` if it is only staged or only in the working tree. Do NOT dismiss a candidate as a false positive just because it is absent from the committed branch diff — check `git status`, `git diff --cached`, and `git diff` (unstaged) to see the full picture.

Parse the output and proceed to Step 2.

## Step 2: Check Attribution Format

**Skip this step for deleted files** — there is nothing to fix in a file that no longer exists.

**Skip this step for `candidate_type: notices_entry` candidates** — these are cross-referenced in Step 3.

Read `CODE_ATTRIBUTION_CRITERIA.md` (in this skill's directory) for the canonical attribution format.

For each actionable non-deleted candidate, read its file content and check for the required fields from `CODE_ATTRIBUTION_CRITERIA.md`.

Extra information beyond the required fields is fine, as are differences in formatting. Only flag **missing** fields.

Record which fields are missing for each file.

## Step 3: Interpret Script Results

For each candidate, determine one **primary finding** and zero or more **additional warnings**.

### NOTICES-entry findings (for `candidate_type: notices_entry`)

For candidates with `candidate_type: notices_entry`, determine one finding per entry:

1. **Entry removed** (`notices_change: removed`) — Read the `scope_text` to identify the source files/packages referenced by this entry. Check whether the referenced files still exist in the repo (use `Glob` or `Read`). If they still exist and contain third-party attribution headers referencing the removed library, flag: the NOTICES entry was removed but vendored source files still reference this library — either restore the entry or remove the vendored code. If the referenced files were also deleted in this branch (they would appear as separate `candidate_type: source_file` candidates with `status: D`), this is consistent — note it as informational only.

2. **Entry modified** (`notices_change: modified`) — The entry content was changed. Read the current `THIRD_PARTY_NOTICES.md` entry and the source files referenced in the scope text. Compare the entry's metadata (Source, License, Copyright) against the source file headers. Flag any inconsistencies (e.g., copyright year mismatch, license mismatch). If the entry is consistent with the source files, note it as informational only.

### Source-file primary finding (one per file — use the first that matches)

Work through this list top-to-bottom and stop at the first match:

1. **Deleted** (`status: D`) — report: "Deleted vendored file — verify `THIRD_PARTY_NOTICES.md` is still accurate."

2. **Attribution removed** (`reasons` contains "attribution markers removed" but not "modified") — flag that attribution markers were removed and should be restored. Show the removed lines.

3. **Attribution modified** (`reasons` contains "attribution markers modified") — remind the user to verify the `THIRD_PARTY_NOTICES.md` entry still matches the updated header.

4. **Renamed** (`status: R`) — flag that the `THIRD_PARTY_NOTICES.md` Scope section likely needs updating to reflect the new path.

5. **No NOTICES match** (`notices_match: none` and `notices_file_exists: true`) — the script could not URL-match this file to a `THIRD_PARTY_NOTICES.md` entry. Read the file's attribution header and `THIRD_PARTY_NOTICES.md`, then attempt a **fuzzy match** by library name, copyright holder, or other context. If you find a match, treat it as matched (use finding 6 instead). If not, flag that the file needs an entry added.

6. **URL match** (`notices_match: url`) — the script found a URL match. Remind the user to verify that the matched entry's Scope section still accurately describes the vendored code.

7. **No NOTICES file** (`notices_file_exists: false`) — `THIRD_PARTY_NOTICES.md` does not exist. Flag that it needs to be created and populated with an entry for this vendored library.

### Additional warnings (zero or more per file, independent of each other)

After determining the primary finding, check each of these independently. Skip all additional warnings for files whose primary finding is **Deleted** or **Attribution removed**.

- **New license type** (`new_license_type: true`) — "-❗This license type is not yet represented in `THIRD_PARTY_NOTICES.md`. Please verify it is compatible with Sentry's licensing policies: https://open.sentry.io/licensing/." (Put this comment in its own bullet point.)

- **NOTICES file not updated** (`notices_file_changed: false` and `notices_file_exists: true`) — if vendored code was added, modified, or renamed but `THIRD_PARTY_NOTICES.md` was not touched, remind that it may need updating.

## Step 4: Output Results

### No issues found

Print "No attribution issues found."

```bash
EXISTING_COMMENT_ID=$(gh api "repos/$OWNER/$REPO/issues/$PR_NUMBER/comments" \
  --paginate --jq '[.[] | select(.body | startswith("<!-- sentry-attribution-check -->")) | .id] | first // empty')

if [[ -n "$EXISTING_COMMENT_ID" ]]; then
  gh api "repos/$OWNER/$REPO/issues/comments/$EXISTING_COMMENT_ID" -X DELETE
fi
```

### Issues found

Print findings to the terminal, grouped by file. Prefix lines that require immediate action with ⚠️ — these represent invalid or missing attribution that must be fixed before merging. Informational reminders (verify, check) should have a 👀 prefix.

Use the following format (only include lines that are relevant; number each entry; omit entries if the user doesn't have to do anything; don't add any sections besides Action Items and/or False Positivies; omit Action Items or False Positives sections if none found; always use the bullet point (`-`) when including a line that has a bullet point below, and put each bullet point on its own line; wrap lines when they reach the edge of the "Outcome of check-code-attribution" box):

```
**********************************************************************************
*                        Outcome of check-code-attribution                       *
**********************************************************************************

----------------------------------- Action items ---------------------------------

1. ⚠️ File: <Fully qualified name of file, e.g., io.sentry.cache.tape.FileObjectQueue>
   Vendored code detected (<Library Name>) — missing required fields:
     - <Summarize what happened and what's needed based on the license header template in `CODE_ATTRIBUTION_CRITERIA.md`. Don't repeat yourself and don't repeat info from the lines above in this notice; keep your output very concise. Prefer summaries to bullet point lists of missing info. Don't insist on the format from `CODE_ATTRIBUTION_CRITERIA.md`; we only care that all info is present.>
     - <If there's no corresponding entry in `THIRD_PARTY_NOTICES.md`, inform the user that they need to add one. Keep discussion of THIRD_PARTY_NOTICES.md` in a separate bullet point from discussions of the license header. Omit this line if there's nothing the user needs to do with respect to `THIRD_PARTY_NOTICES.md`.>
```

For files where attribution markers were removed:
```
1. ⚠️ File: <Fully qualified name of file>
   Required attribution field(s) removed:
     - <Summarize what happened and what's needed based on the license header template in `CODE_ATTRIBUTION_CRITERIA.md`. Don't repeat yourself and don't repeat info from the lines above in this notice; keep your output very concise. Prefer summaries to bullet point lists of missing info. Don't insist on the format from `CODE_ATTRIBUTION_CRITERIA.md`; we only care that all info is present. No need to specify individuals fields if the entire header was removed and restoring it would satisfy our criteria: just tell the user to restore it. No need to tell them why they need to restore it.>
     - <If there's no corresponding entry in `THIRD_PARTY_NOTICES.md`, inform the user that they need to add one. Keep discussion of THIRD_PARTY_NOTICES.md` in a separate bullet point from discussions of the license header. Omit this line if there's nothing the user needs to do with respect to `THIRD_PARTY_NOTICES.md`.>
```

For files with a matching THIRD_PARTY_NOTICES.md entry:
```
1. 👀 File: <Fully qualified name of file>
   Vendored code detected (<Library Name>) – verify that `THIRD_PARTY_NOTICES.md` reflects your updates.
```

For renamed files:
```
1. 👀 File: <Fully qualified name of file>
   Vendored file renamed – Verify `THIRD_PARTY_NOTICES.md` reflects your updates.
```

For deleted files:
```
1. 👀 File: <Fully qualified name of file>
   Deleted vendored file – Verify `THIRD_PARTY_NOTICES.md` reflects your updates.
```

For removed NOTICES entries where source files still exist:
```
1. ⚠️ NOTICES entry removed: <Heading>
   Source file(s) still reference this library:
     - `<package.ClassName>` still contains attribution header for <Library Name>. Either restore the `THIRD_PARTY_NOTICES.md` entry or remove the vendored code.
```

For modified NOTICES entries with inconsistencies:
```
1. ⚠️ NOTICES entry modified: <Heading>
   Entry metadata inconsistent with source file headers:
     - <Describe the inconsistency, e.g., copyright year mismatch>
```

For modified NOTICES entries that are consistent:
```
1. 👀 NOTICES entry modified: <Heading>
   Verify updated entry is consistent with source file headers.
```

```

---------------------------------- False positives -------------------------------

<Numbered list of any false positives, starting at 1., plus descriptions for each as to why they aren't true positives. Keep it very short, and don't repeat yourself.>
```

If there are no Action Items, print the following after *all* sections: "✅ Everything looks good. No attribution issues found."
