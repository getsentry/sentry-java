---
name: check-code-attribution
description: Per-file check of vendored code attribution in the current branch diff, including license headers, THIRD_PARTY_NOTICES.md entries, and compatibility with Sentry's licensing policy
allowed-tools: Bash Read Grep Glob
---

# Check Code Attribution

You are reviewing changed files for third-party code attribution compliance in **sentry-java**, an MIT-licensed repository.

## Local runs — discover changed files first

When running locally (not via Warden), determine which files changed on this branch:

```bash
MB=$(git merge-base HEAD origin/main 2>/dev/null || git merge-base HEAD main)
git diff --name-only "${MB}"..HEAD
```

Then run the Quick triage and subsequent checks on **every** file in that list. Warden's `ignorePaths` in `warden.toml` lists the paths to skip — apply the same exclusions locally.

### Warden CLI (optional local parity check)

Warden does **not** use Cursor auth. Before running Warden locally, configure a provider (same model family as `warden.toml`, or override with `-m`):

```bash
# Option A: Anthropic API key (matches CI model in warden.toml)
export WARDEN_ANTHROPIC_API_KEY=sk-ant-...   # or: export ANTHROPIC_API_KEY=sk-ant-...

# Option B: Pi OAuth / API key store (~/.pi/agent/auth.json)
npx pi    # then run /login and pick Anthropic (or another provider)

# Option C: Different provider for a one-off run
export WARDEN_OPENAI_API_KEY=sk-...
npx @sentry/warden origin/main..HEAD --skill check-code-attribution -m openai/gpt-5.5 -vv
```

```bash
npx @sentry/warden origin/main..HEAD --skill check-code-attribution -vv
```

If you only need attribution review in the IDE, `/check-code-attribution` in Cursor does not require Warden credentials.

When running via Warden, the changed file is already provided — skip branch-wide discovery, but follow **Warden execution** below.

## Warden execution

Warden analyzes one changed file per run (whole-file mode). Complete every Quick triage step — the diff alone is not sufficient.

**Mandatory on every run (do not skip):**

1. `Read` the first 50 lines of the changed file.
2. `Grep` `THIRD_PARTY_NOTICES.md` for the class name (filename without extension, e.g. `ANRWatchDog` for `ANRWatchDog.java`). On renames, also grep the old basename and read Scope sections (see Quick triage).
3. When Bash is available, compare the merge-base header:
   ```bash
   MB=$(git merge-base HEAD origin/main 2>/dev/null || git merge-base HEAD main)
   git show "${MB}:<file-path>" | head -50
   ```

**Do not dismiss findings because:**

- A `THIRD_PARTY_NOTICES.md` entry exists — file headers are still required; NOTICES does not replace them.
- The diff only removes a header comment block — removed `-` lines with `Copyright`, `Licensed under`, license disclaimers, or vendoring language ("adapted from", etc.) mean attribution was stripped.
- The header says "Adapted from …" but omits copyright holder or license name — flag missing header fields.

For `THIRD_PARTY_NOTICES.md` runs: for every **removed** entry in the diff, use `Read` or `Glob` to confirm whether Scope files still exist with attribution headers. If they do, the entry must not be removed.

## Quick triage

Sentry's own files carry **no** copyright headers — any copyright/license line indicates third-party code. Every file that reaches this skill is in scope — do not skip files based on extension.

If this file is `THIRD_PARTY_NOTICES.md`, go to the THIRD_PARTY_NOTICES section below.

For all other files, perform these checks **before** deciding whether to proceed:

1. **Read the file header** — use the Read tool to read the first 50 lines of the file. Look for vendored-code signals: `Copyright`, `Licensed under`, `SPDX-License-Identifier`, or vendoring language ("adapted from", "backported from", "based on", "copied from", "derived from", "inspired by", "ported from", "translated from", "vendored").
2. **Check THIRD_PARTY_NOTICES.md** — use Grep to search `THIRD_PARTY_NOTICES.md` for the file name without extension (e.g., search for `ANRWatchDog` when reviewing `ANRWatchDog.java`). A match means this is a known vendored file. **Renames:** if the diff is a rename (`similarity index` / `rename from` in the diff, or a delete of one path and add of another with the same content), also Grep for the **old** basename and read **Scope** sections in matching entries — NOTICES may still reference the previous class or path name.
3. **Scan the diff** — check for vendored-code signals on both added (`+`) and **removed (`-`)** lines. Removed copyright/license lines ARE signals — they mean attribution is being stripped.

**A signal in ANY of these three sources means this is vendored code — proceed to the vendored source file section.**

A file referenced in THIRD_PARTY_NOTICES.md is ALWAYS vendored, even if its current header has no attribution.

**If none of the three sources have signals, report no findings and stop.**

---

## If this file is `THIRD_PARTY_NOTICES.md`

Validate the changed entries using the diff context:

1. For each added or modified entry, verify it has all required fields: **Source URL**, **License name**, **Copyright**, **Scope** (file paths), and **full license text** in a fenced code block.
2. For each Scope path, verify the file(s) exist (use Glob or Read).
3. Flag new license types — especially copyleft or AGPL.
4. Flag orphaned entries whose Scope files no longer exist.
5. For **removed** entries (lines prefixed with `-` in the diff), use Read to check whether the Scope files still exist and still have attribution headers. If they do, the entry must not be removed.
6. Check **copyright consistency** — the Copyright field must match the copyright line inside the embedded license text. Flag mismatches.

---

## If this is a vendored file

### 1. Check attribution header

The file must have a license header near the top (before the `package` statement in Java/Kotlin files) with:
- Library name or origin
- Copyright year and holder
- License name
- Source URL

Exact wording and comment style may vary. Only flag **missing fields**, not formatting.

Compare the current header (from the Read in Quick triage) against the THIRD_PARTY_NOTICES.md entry. For example, if the NOTICES entry says this file is MIT-licensed by "Salomon BRYS" but the current header has no copyright or license mention, the header was stripped.

When Bash is available (local runs), also compare against the merge-base version for additional context:
```bash
MB=$(git merge-base HEAD origin/main 2>/dev/null || git merge-base HEAD main)
git show "${MB}:<file-path>" | head -50
```

Flag these issues:
- **Header stripped** — file is in NOTICES but current header has no attribution
- **Header truncated** — header is present but missing required fields (e.g., copyright line removed, license disclaimer removed)
- **Header inconsistent** — header contradicts what the NOTICES entry says
- **Diff removes attribution lines** — `Copyright`, `Licensed under`, etc. appear on removed lines in the diff

### 2. Check THIRD_PARTY_NOTICES.md entry

From the Grep in Quick triage: if no matching entry exists, flag it as missing. A valid entry needs: Source URL, License name, Copyright, Scope, full license text.

### 3. Check license compatibility

Classify the license per Sentry's Open Source Legal Policy (https://open.sentry.io/licensing/):

| Tier            | Examples                                        | Finding                                     |
|-----------------|-------------------------------------------------|---------------------------------------------|
| Permissive      | MIT, BSD, Apache 2.0, ISC, CC0, Unlicense, Zlib | None — license is compatible                |
| Weak copyleft   | LGPL, MPL, EPL, CDDL                            | 🚨 **high** — requires review               |
| Strong copyleft | GPL, QPL, Sleepycat, OSL                        | 🚨 **high** — requires legal review         |
| AGPL            | —                                               | 🚨 **high** — absolute ban, must be removed |
| No license      | —                                               | 🚨 **high** — assume no permission          |

**Permissive licenses:** do not report a finding solely because the license is MIT/BSD/Apache/etc. Only flag attribution problems (missing or stripped header fields, missing/inconsistent `THIRD_PARTY_NOTICES.md` entry). Copyleft and unlicensed code still get 🚨 findings per the table.

---

## If this is a deleted vendored file

If the diff deletes a file and the removed lines contained attribution headers, check whether `THIRD_PARTY_NOTICES.md` still references it — the entry should be updated or removed.

---

## Severity guide

| Level      | Use for                                                                                                                                             |
|------------|-----------------------------------------------------------------------------------------------------------------------------------------------------|
| **high**   | 🚨 License violations: AGPL, copyleft, unlicensed, no-license code                                                                                  |
| **medium** | ⚠️ Missing attribution header fields, stripped headers, missing/inconsistent NOTICES entries, deleted/renamed vendored files needing NOTICES update |
| **low**    | 👀 Attribution present but could be improved                                                                                                        |

Warden relies on these severity levels when deciding whether to comment on PRs or require changes. Put the severity emoji **only on the finding title** (see Output) so reviewers can triage at a glance.

## Output

**No issues → empty response (say nothing).**

Otherwise, report each finding ordered by severity (most severe first).

### Emoji placement (required)

Use the emoji from the severity guide (🚨, ⚠️, or 👀) — not the word `high`, `medium`, or `low`.

| Field             | Emoji?                   | Example                                                                                            |
|-------------------|--------------------------|----------------------------------------------------------------------------------------------------|
| **Title**         | Yes — once, at the start | `⚠️ Copyright line stripped from vendored file header`                                             |
| **Description**   | **No**                   | `**io.sentry.cache.tape.FileObjectQueue** — The Copyright (C) 2010 Square, Inc. line was removed…` |
| **Verification**  | **No**                   | Evidence steps only                                                                                |
| **Suggested fix** | **No**                   | Fix text only                                                                                      |

**Good (Warden PR comment):**

```
Title:       ⚠️ Copyright line stripped from vendored file header
Description: **io.sentry.cache.tape.FileObjectQueue** — The `Copyright (C) 2010 Square, Inc.` line was removed from this vendored file's header. Please restore the copyright line.
```

**Bad — emoji repeated in the description:**

```
Title:       ⚠️ Copyright line stripped from vendored file header
Description: ⚠️ The `Copyright (C) 2010 Square, Inc.` line was removed…
```

### Warden runs

For each finding, set:

- **title** — `<severity emoji> <short issue title>` (imperative, no class name). Warden bolds this as the PR comment heading.
- **description** — One or two sentences: `**<fully qualified class name>** — <what is wrong and how to fix>`. Do **not** start with an emoji.
- **verification** — Optional evidence steps. No emoji.

Use fully qualified Java class names in the description (e.g. `io.sentry.CircularFifoQueue`), not file paths. For license issues, include the policy link in the description.

### Local / IDE runs

Use this numbered format — same title vs description split as above:

```
1\. <severity emoji> **<short issue title>**
   **<fully qualified class name>** — <what's wrong and how to fix it — one or two lines>

2\. <severity emoji> **<short issue title>**
   **<fully qualified class name>** — <what's wrong and how to fix it — one or two lines>
```

Rules:

- **Escape the period** after the number (`1\.` not `1.`) so markdown does not collapse entries into a tight list.
- Leave an empty line between each numbered finding.
