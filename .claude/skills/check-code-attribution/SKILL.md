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

If the script exits with code 0, there are no candidates. Print "No attribution issues found."

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
   status: A|M|D|R
   reasons: <comma-separated list of why this file was flagged>
   ---
   ```

The script handles candidate identification deterministically: changed-file collection across committed/staged/unstaged layers, path and generated-file exclusions, binary detection, vendor-path detection, attribution-marker detection, and first-party copyright filtering.

**Important:** The script checks three layers of changes: (1) committed changes on the branch vs. the merge-base, (2) staged but uncommitted changes, and (3) unstaged working-tree changes. A candidate may not appear in `git diff merge-base..HEAD` if it is only staged or only in the working tree. Do NOT dismiss a candidate as a false positive just because it is absent from the committed branch diff — check `git status`, `git diff --cached`, and `git diff` (unstaged) to see the full picture.

Parse the output and proceed to Step 2.

## Step 2: Gather Context

Read `CODE_ATTRIBUTION_CRITERIA.md` (in this skill's directory) for the canonical attribution format.

If `notices_file_exists` is `true`, read `THIRD_PARTY_NOTICES.md` to understand existing entries.

## Step 3: Analyze Each Candidate

**Skip analysis for deleted files** (`status: D`) — go straight to the finding: "Deleted vendored file — verify `THIRD_PARTY_NOTICES.md` is still accurate."

For each non-deleted candidate:

1. **Read the file** and check for the required attribution fields from `CODE_ATTRIBUTION_CRITERIA.md`. Extra information beyond the required fields is fine, as are differences in formatting. Only flag **missing** fields.

2. **Match to a `THIRD_PARTY_NOTICES.md` entry** — Try to find a corresponding entry by URL, library name, copyright holder, or other context. Record whether you found a match, and if so, which entry.

3. **Check the license type** — Identify the license declared in the file's header. Check whether this license type is already represented in `THIRD_PARTY_NOTICES.md` headings. If it's a new license type not yet in NOTICES, flag it for compatibility review.

## Step 4: Check for NOTICES Entry Changes

If `notices_file_changed` is `true`, compare old vs. new `THIRD_PARTY_NOTICES.md`:

```bash
git show $(git merge-base HEAD origin/main 2>/dev/null || git merge-base HEAD main):THIRD_PARTY_NOTICES.md
```

Check for:

1. **Removed entries** — Headings present in the old version but absent in the new. For each, check whether the source files referenced in the entry's Scope section still exist in the repo (use `Glob` or `Read`). If they still exist and contain third-party attribution headers, flag: the NOTICES entry was removed but vendored source files still reference this library. If the referenced files were also deleted in this branch, note it as informational only.

2. **Modified entries** — Headings present in both but with changed content. Read the source files referenced in the entry's Scope section and compare the entry's metadata (Source, License, Copyright) against the source file headers. Flag any inconsistencies. If the entry is consistent with the source files, note it as informational only.

## Step 5: Output Results

### No issues found

Print "No attribution issues found."

### Issues found

Print findings to the terminal, grouped by file. Prefix lines that require immediate action with ⚠️. Informational reminders (verify, check) get a 👀 prefix.

Use the following format (only include lines that are relevant; number each entry; omit entries if the user doesn't have to do anything; omit Action Items or False Positives sections if none found; put each bullet point on its own line; wrap lines when they reach the edge of the "Outcome of check-code-attribution" box):

```
**********************************************************************************
*                        Outcome of check-code-attribution                       *
**********************************************************************************

----------------------------------- Action items ---------------------------------

1. ⚠️ File: <Fully qualified name of file, e.g., io.sentry.cache.tape.FileObjectQueue>
   Vendored code detected (<Library Name>) — missing required fields:
     - <Summarize what happened and what's needed based on the license header template in `CODE_ATTRIBUTION_CRITERIA.md`. Don't repeat yourself and don't repeat info from the lines above in this notice; keep your output very concise. Prefer summaries to bullet point lists of missing info. Don't insist on the format from `CODE_ATTRIBUTION_CRITERIA.md`; we only care that all info is present.>
     - <If there's no corresponding entry in `THIRD_PARTY_NOTICES.md`, inform the user that they need to add one. Keep discussion of `THIRD_PARTY_NOTICES.md` in a separate bullet point from discussions of the license header. Omit this line if there's nothing the user needs to do with respect to `THIRD_PARTY_NOTICES.md`.>
```

For files where attribution markers were removed:
```
1. ⚠️ File: <Fully qualified name of file>
   Required attribution field(s) removed:
     - <Summarize what happened and what's needed. No need to specify individual fields if the entire header was removed and restoring it would satisfy our criteria: just tell the user to restore it.>
     - <If there's no corresponding entry in `THIRD_PARTY_NOTICES.md`, inform the user. Omit if not applicable.>
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

For new license types:
```
     - ❗This license type is not yet represented in `THIRD_PARTY_NOTICES.md`. Please verify it is compatible with Sentry's licensing policies: https://open.sentry.io/licensing/.
```

```

---------------------------------- False positives -------------------------------

<Numbered list of any false positives, starting at 1., plus descriptions for each as to why they aren't true positives. Keep it very short, and don't repeat yourself.>
```

If there are no Action Items, print the following after *all* sections: "✅ Everything looks good. No attribution issues found."
