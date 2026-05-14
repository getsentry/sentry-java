---
name: check-code-attribution
description: Check vendored code attributions in branch diff and flag any that are deficient. Use when asked to "check attribution", "check licenses", "verify vendored code attribution", or "check code attribution".
allowed-tools: Bash, Read
---

# Check Code Attribution

Verify that vendored/adapted third-party code in the current branch diff has correct license headers and `THIRD_PARTY_NOTICES.md` entries.

## Step 1: Run the Candidate Detection Script

Run the pre-filter script:

```bash
bash .claude/skills/check-code-attribution/find-attribution-candidates.sh
```

Stdout **always** starts with **global metadata** (first two lines):

```
notices_file_exists: true|false
notices_file_changed: true|false
```

If the script exits with any code besides 0 or 10, it failed (e.g., could not determine merge-base). Print the stderr output and **stop**.

If the script exits with code **0**, there are no file candidates **and** `THIRD_PARTY_NOTICES.md` is unchanged vs the merge-base. Print "✅ No attribution issues found." and **stop**.

If the script exits with code **10**, there is at least one file candidate **and/or** `THIRD_PARTY_NOTICES.md` changed (including NOTICES-only edits that produce **zero** candidate blocks). After the two metadata lines, stdout may contain zero or more **candidate blocks**:

```
---
file: <path>
status: A|M|D|R (i.e., "added", "modified", "deleted", "renamed")
reasons: <comma-separated list of why this file was flagged>
---
```

The script handles candidate identification deterministically — including committed, staged, and unstaged changes — so trust its output. Do not dismiss a candidate as a false positive based on the committed diff alone.

Parse the metadata and any candidate blocks, then proceed to Step 2. If there are **zero** candidate blocks but `notices_file_changed` is `true`, skip Step 3 and still run Step 4.

## Step 2: Gather Context

Read `CODE_ATTRIBUTION_CRITERIA.md` (in this skill's directory) for the canonical attribution format.

If `notices_file_exists` is `true`, read `THIRD_PARTY_NOTICES.md` to understand existing entries.

## Step 3: Analyze Each Candidate

**Skip analysis for deleted files** (`status: D`) — they only need a 👀 verify finding.

For each non-deleted candidate:

1. **Read the file** and check for the required attribution fields from `CODE_ATTRIBUTION_CRITERIA.md`: The criteria shows a canonical template, but the exact wording, comment style, and formatting don't need to match exactly. Only flag **missing** fields. For candidates whose reasons include "removed", also read the merge-base version (`git show "$MB:<path>"`) to see what attribution was there before — you need both versions to determine whether attribution was stripped vs. never present.

2. **Match to a `THIRD_PARTY_NOTICES.md` entry** — Try to find a corresponding entry by URL, library name, copyright holder, or other context. Record whether you found a match, and if so, which entry.

3. **Check license compatibility** — Identify the license in the file's header and classify it. sentry-java is MIT-licensed. Sentry's Open Source Legal Policy (https://open.sentry.io/licensing/) defines four tiers:
   - **Permissive** (MIT, BSD, Apache 2.0, ISC, CC-BY, CC0, Unlicense, WTFPL, Zlib, etc.) — allowed. No action needed.
   - **Weak copyleft** (LGPL, MPL, EPL, CDDL, CPL, etc.) — may be allowed for vendoring but requires review. Flag as **Critical** with a note to verify against the policy.
   - **Strong copyleft** (GPL, QPL, Sleepycat, OSL, etc.) — flag as **Critical**, requires legal review before vendoring.
   - **AGPL** — **absolute ban**, must not be used at Sentry for any use case. Flag as **Critical** and block.
   - **No license** — assume no permission to use. Flag as **Critical**.

   Also check whether this license type is already represented in `THIRD_PARTY_NOTICES.md` headings; if it's new, note it.

## Step 4: Check for NOTICES Entry Changes

If `notices_file_changed` is `true`, compare the merge-base revision of `THIRD_PARTY_NOTICES.md` to the current file. Resolve merge-base the same way as `find-attribution-candidates.sh` (`origin/main`, else `main`):

```bash
MB=$(git merge-base HEAD origin/main 2>/dev/null || git merge-base HEAD main)
```

- **Old (merge-base) content:** `git show "$MB:THIRD_PARTY_NOTICES.md"` — if this fails (file absent at merge-base), treat the old side as empty.
- **New (current) content:** read `THIRD_PARTY_NOTICES.md` from the repo root when `notices_file_exists` is `true`. When `notices_file_exists` is `false`, the file is gone from the worktree (for example deleted on the branch); treat the new side as empty so every merge-base entry shows up as removed for analysis.

Compare old vs. new and verify that every `THIRD_PARTY_NOTICES.md` entry is consistent with the source file headers from the diff: metadata matches, no orphaned or missing entries, no stale Scope paths. Skip entries whose source files were already analyzed in Step 3 — they're covered there. Any entries with new license types (e.g., AGPL where no other entry has an AGPL license) must be flagged as **Critical**.

**Also check even when `notices_file_changed` is `false`:** if the branch deletes or renames a source file (status D or R), verify that the corresponding NOTICES entry was updated or removed. This catches the case where NOTICES *should* have changed but didn't.

## Step 5: Output Results

If there are no issues, print:

```
✅ No attribution issues found.
```

Otherwise, print findings as a numbered list. Use fully qualified class names (e.g., `io.sentry.cache.tape.FileObjectQueue`). Guidelines:

- **🚨** = license issue (AGPL, strong copyleft, weak copyleft, new license type, unlicensed code). Goes in the **Critical** section.
- **⚠️** = must fix before merging (missing fields, stripped attribution, inconsistent or orphaned NOTICES entries). Goes in the **Urgent** section.
- **👀** = author should verify (deleted/renamed files, matched NOTICES entries, consistent NOTICES modifications). Goes in the **Verify** section.
- Keep license-header issues and `THIRD_PARTY_NOTICES.md` issues in separate bullets.
- For license concerns, link the policy: https://open.sentry.io/licensing/
- Be **very, very** concise — say what's wrong and what to do in as few words as possible!
- If any candidates are false positives, list them at the end with a one-line reason each.
- Separate each numbered entry with an empty line for readability (see example "Urgent" output below).
- Omit any section that has no entries.

Example output:

```
Code Attribution Check
══════════════════════

Critical
────────
1. 🚨 io.sentry.util.AgplHelper
   AGPL-licensed code — absolute ban per Sentry policy. Must be removed.
   - Policy: https://open.sentry.io/licensing/

Urgent
──────
2. ⚠️ io.sentry.util.TokenBucket
   Vendored code (Guava) — header is missing the source URL and copyright 
   year.
   - No corresponding `THIRD_PARTY_NOTICES.md` entry; add one.

3. ⚠️ io.sentry.android.core.ANRWatchDog
   MIT license header was stripped. Restore the attribution header.

Verify
──────
4. 👀 io.sentry.cache.tape.FileObjectQueue
   Vendored code (Square Tape) — verify `THIRD_PARTY_NOTICES.md` reflects
   your updates.

False positives
───────────────
a. AGENTS.md — project documentation, not vendored code.
```
