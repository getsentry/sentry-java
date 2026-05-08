---
name: check-code-attribution
description: Per-file check of vendored code attribution in the current branch diff, including license headers, THIRD_PARTY_NOTICES.md entries, and compatibility with Sentry's licensing policy
allowed-tools: Bash Read Grep Glob
---

**Maintainers:** Only edit files in `.claude/skills/check-code-attribution` (the committed file) and run `npx @sentry/dotagents sync` from the command line to automatically update the matching files in `.agents/skills/check-code-attribution`.

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
- The diff only removes a header comment block — if removed `-` lines include a **required field** (see below) or vendoring language ("adapted from", etc.), attribution was stripped. Removing boilerplate alone is not stripping.
- The header says "Adapted from …" but omits copyright holder or license name — flag missing header fields.
- The file header has all four required fields — a missing THIRD_PARTY_NOTICES.md entry is independently required and is ⚠️ medium regardless of header completeness.

For `THIRD_PARTY_NOTICES.md` runs: for every **removed** entry in the diff, use `Read` or `Glob` to confirm whether Scope files still exist with attribution headers. If they do, the entry must not be removed.

## Quick triage

Sentry's own files carry **no** copyright headers — any copyright/license line indicates third-party code. Every file that reaches this skill is in scope — do not skip files based on extension.

If this file is `THIRD_PARTY_NOTICES.md`, go to the THIRD_PARTY_NOTICES section below.

For all other files, perform these checks **before** deciding whether to proceed:

1. **Read the file header** — use the Read tool to read the first 50 lines of the file. Look for vendored-code signals: `Copyright`, `Licensed under`, `SPDX-License-Identifier`, or vendoring language ("adapted from", "backported from", "based on", "copied from", "derived from", "inspired by", "ported from", "translated from", "vendored").
2. **Check THIRD_PARTY_NOTICES.md** — use Grep to search `THIRD_PARTY_NOTICES.md` for the file name without extension (e.g., search for `ANRWatchDog` when reviewing `ANRWatchDog.java`). A match means this is a known vendored file. **Renames:** if the diff is a rename (`similarity index` / `rename from` in the diff, or a delete of one path and add of another with the same content), also Grep for the **old** basename and read **Scope** sections in matching entries — NOTICES may still  reference the previous class or path name.
    > **A complete NOTICES entry does NOT end the check.** It confirms the file is vendored and that the NOTICES requirement is satisfied. The file header is a separate, additional requirement — continue to header verification regardless of NOTICES completeness.
3. **Scan the diff** — check for vendored-code signals on both added (`+`) and **removed (`-`)** lines. Removed lines that drop a **required field** (copyright, license name, source URL, vendoring origin) ARE signals. Removed disclaimer/boilerplate lines alone are not.

**A signal in ANY of these three sources means this is vendored code — proceed to the vendored source file section.**

A file referenced in THIRD_PARTY_NOTICES.md is ALWAYS vendored, even if its current header has no attribution.

**If none of the three sources have signals, report no findings and stop.**

---

## If this file is `THIRD_PARTY_NOTICES.md`

Validate the changed entries using the diff context:

1. For each added or modified entry, verify it has all required fields: **Source URL**, **License name**, **Copyright**, **Scope** (file paths), and **full license text** in a fenced code block.
2. For each Scope path, verify the file(s) exist (use Glob or Read).
3. Flag new license types using the same license-tier table as for source files: weak copyleft (LGPL, MPL, EPL) → 🚨 **high**, strong copyleft (GPL) → 🚨 **high**, AGPL → 🚨 **high** (absolute ban, must be removed). Do not use low or medium for copyleft or AGPL.
4. Flag orphaned entries whose Scope files no longer exist.
5. For **removed** entries (lines prefixed with `-` in the diff), use Read to check whether the Scope files still exist and still have attribution headers. If they do, the entry must not be removed.
6. Check **copyright consistency** — the Copyright field must match the copyright line inside the embedded license text. Flag mismatches.

---

## If this is a vendored file

### 1. Check attribution header

Check each of the following by reading the file header — not NOTICES. Each is an independent yes/no; a "no" is ⚠️ medium regardless of NOTICES completeness:

- [ ] **Vendoring origin phrase** — explicit wording such as `Adapted from …`, `Based on …`, `Vendored from …`, or a library name.
- [ ] **Copyright line** — e.g. `Copyright (c) 2016 …`, `Copyright 2010 Square, Inc.`
- [ ] **License name** — e.g. `Licensed under the Apache License, Version 2.0`, `The MIT License`
- [ ] **Source URL** — e.g. `https://github.com/…`

Exact wording and comment style may vary. **Do not flag** missing or changed content that is not one of these four fields.

**Each field must be physically present in the file header. A complete `THIRD_PARTY_NOTICES.md` entry does not satisfy any required field — both are independently required. Check each of the four fields by reading the file header, not by reasoning from NOTICES.**

**Not required in the file header** (full text belongs in `THIRD_PARTY_NOTICES.md`, not in every source file):

- Full license boilerplate (MIT permission paragraph, Apache "Unless required by applicable law…" disclaimer, ASF contributor grant preamble)
- Wording differences vs the NOTICES embedded license text (e.g. shortened Apache header vs canonical ASF phrasing)
- Comment style (`//` vs `/* */`), line wrapping, or extra Sentry modification notes

Compare the current header against the NOTICES entry **only for the four required fields** — e.g. if NOTICES says MIT by "Salomon BRYS" but the header has no copyright or license name, flag it. If both have copyright + license name but the header omits the Apache disclaimer while NOTICES still has the full text, **do not flag**.

When Bash is available (local runs), also compare against the merge-base version for additional context:
```bash
MB=$(git merge-base HEAD origin/main 2>/dev/null || git merge-base HEAD main)
git show "${MB}:<file-path>" | head -50
```

Flag these issues:
- **Header stripped** — file is in NOTICES but current header has none of the four required fields
- **Header truncated** — one or more **required** fields were removed (e.g. copyright line or `Licensed under …` removed) while the file remains vendored
- **Header inconsistent** — a **required** field contradicts NOTICES (wrong copyright holder/year, wrong license name) — not boilerplate or phrasing differences
- **Diff removes required attribution** — removed `-` lines drop a required field or vendoring origin (`Adapted from`, etc.); removing disclaimer/boilerplate lines alone is **not** this

**Do not report** (no finding — prefer silence):

- Apache/MIT disclaimer or permission paragraphs removed but all four required fields remain
- Header reworded to a shorter permissive-license form with the same copyright holder and license name
- Header and NOTICES differ only in full license body text (wording or boilerplate, not missing required fields)

These exceptions apply only when an entry already exists in NOTICES and only to header-vs-NOTICES wording differences. A **missing** NOTICES entry is ⚠️ medium per section 2 — never covered by these exceptions.

### 2. Check THIRD_PARTY_NOTICES.md entry

**Severity: always `medium`. Do not output `severity: "low"` for a missing entry even if the attribution header is complete.**

`THIRD_PARTY_NOTICES.md` is a mandatory legal exhibit that Sentry ships with every SDK distribution. It must enumerate all vendored code regardless of what the source file header says. A missing entry is a distribution-level compliance failure, not a nit. A complete file header does not satisfy the NOTICES requirement — both are mandatory.

From the Grep in Quick triage: if no matching entry exists, output `severity: "medium"` and flag as ⚠️ Missing THIRD_PARTY_NOTICES.md entry. A valid entry needs: Source URL, License name, Copyright, Scope, full license text.

### 3. Check license compatibility

Classify the license per Sentry's Open Source Legal Policy (https://open.sentry.io/licensing/):

| Tier            | Examples                                        | Finding                                     |
|-----------------|-------------------------------------------------|---------------------------------------------|
| Permissive      | MIT, BSD, Apache 2.0, ISC, CC0, Unlicense, Zlib | None — license is compatible                |
| Weak copyleft   | LGPL, MPL, EPL, CDDL                            | 🚨 **high** — requires review               |
| Strong copyleft | GPL, QPL, Sleepycat, OSL                        | 🚨 **high** — requires legal review         |
| AGPL            | —                                               | 🚨 **high** — absolute ban, must be removed |
| No license      | —                                               | 🚨 **high** — assume no permission          |

**Permissive licenses:** do not report a finding solely because the license is MIT/BSD/Apache/etc. Only flag missing or stripped **required** header fields, or missing/inconsistent `THIRD_PARTY_NOTICES.md` entry. Do not flag disclaimer/boilerplate-only diffs. Copyleft and unlicensed code still get 🚨 findings per the table.

---

## If this is a deleted vendored file

If the diff deletes a file and the removed lines contained attribution headers, check whether `THIRD_PARTY_NOTICES.md` still references it — the entry should be updated or removed.

---

## Severity guide

| Level      | Use for                                                                                                                                                                                     |
|------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **high**   | 🚨 License violations: AGPL, copyleft, unlicensed, no-license code                                                                                                                          |
| **medium** | ⚠️ Missing **required** header fields, stripped required fields, missing/inconsistent NOTICES entries (even when header is complete), deleted/renamed vendored files needing NOTICES update |
| **low**    | 👀 Cosmetic/style differences only (shortened license wording, comment style). **Never** use for a missing NOTICES entry or missing header field — those are always medium.                 |

Warden relies on these severity levels when deciding whether to comment on PRs or require changes. Put the severity emoji **only on the finding title** (see Output) so reviewers can triage at a glance.

## Output

**No issues → empty response (say nothing).**

Otherwise, report each finding ordered by severity (most severe first).

### Emoji placement (required)

Use the emoji from the severity guide (🚨, ⚠️, or 👀) — not the word `high`, `medium`, or `low`.

| Field             | Emoji?                   | Example                                                                                                                                |
|-------------------|--------------------------|----------------------------------------------------------------------------------------------------------------------------------------|
| **Title**         | Yes — once, at the start | `⚠️ Copyright line stripped from vendored file header`                                                                                 |
| **Description**   | **No**                   | `**io.sentry.cache.tape.FileObjectQueue** — The Copyright (C) 2010 Square, Inc. line was removed…` (see **Description subject** below) |
| **Verification**  | **No**                   | Evidence steps only                                                                                                                    |
| **Suggested fix** | **No**                   | Fix text only                                                                                                                          |

**Good (Warden PR comment):**

```
Title:       ⚠️ Copyright line stripped from vendored file header
Description: **io.sentry.cache.tape.FileObjectQueue** — The `Copyright (C) 2010 Square, Inc.` line was removed from this vendored file's header. Please restore the copyright line.
```

**Bad — emoji in the description (never do this):**

```
Title:       ⚠️ Copyright line stripped from vendored file header
Description: ⚠️ The `Copyright (C) 2010 Square, Inc.` line was removed…
```

**Bad — emoji before the class name:**

```
Title:       ⚠️ Copyright line stripped from vendored file header
Description: ⚠️ **io.sentry.cache.tape.FileObjectQueue** — The copyright line was removed…
```

### Description subject (required)

Every description **must** start with `**<subject>** —` (bold subject, space, em dash, space). Pick **one** subject by file type:

| File type                                                                                 | Subject format                                                       | Example                                                        |
|-------------------------------------------------------------------------------------------|----------------------------------------------------------------------|----------------------------------------------------------------|
| Java / Kotlin source (`.java`, `.kt`) with a top-level type                               | Fully qualified class name (FQCN)                                    | `**io.sentry.CircularFifoQueue** —`                            |
| Java / Kotlin with no single clear type (multiple top-level types, unclear which changed) | FQCN of the primary type under review, or repo-relative path if none | `**sentry/src/.../Foo.kt** —`                                  |
| `THIRD_PARTY_NOTICES.md`                                                                  | `THIRD_PARTY_NOTICES.md — <entry heading>`                           | `**THIRD_PARTY_NOTICES.md — Square — Seismic (Apache 2.0)** —` |
| Gradle / other scripts (e.g. `.kts`, `.gradle`)                                           | Repo-relative path from repository root                              | `**build.gradle.kts** —`                                       |

- Prefer **FQCN** for `.java` / `.kt` vendored source (derive from `package` + primary public top-level class). Do not use file paths when a FQCN is clear.
- For license-tier / policy issues, include https://open.sentry.io/licensing/ in the description body.

### Warden runs

For each finding, set these fields exactly:

| Field            | Value                                                                                                             |
|------------------|-------------------------------------------------------------------------------------------------------------------|
| **severity**     | `high`, `medium`, or `low` — **never** put emoji here; Warden maps severity from this field, not from the title   |
| **title**        | `<severity emoji> <short issue title>` — emoji allowed **only** here (imperative, no class name)                  |
| **description**  | `**<subject>** — <what is wrong and how to fix>` — **plain text only**; subject per **Description subject** above |
| **verification** | Optional evidence steps — plain text only                                                                         |

**Description rules (Warden):**

- **Must** match `**<subject>** — …` using the table in **Description subject**.
- **Must not** contain 🚨, ⚠️, 👀, or the words `high`, `medium`, or `low` as severity labels.
- **Must not** repeat the title or paraphrase it with an emoji prefix.

**Good (NOTICES entry removed while scope files remain):**

```
Title:       ⚠️ NOTICES entry removed for vendored code still in tree
Description: **THIRD_PARTY_NOTICES.md — Square — Seismic (Apache 2.0)** — The Seismic entry was removed but `io.sentry.android.core.SentryShakeDetector` still has an attribution header. Restore the entry or remove attribution from the scope files.
```

**Before submitting findings:** For every finding, confirm `description` does not match `[🚨⚠️👀]` and matches `^\*\*.+\*\* — `. If it contains any emoji, rewrite the description without it.

### Local / IDE runs

Use this numbered format — same title vs description split as above:

```
1\. <severity emoji> **<short issue title>**
   **<subject>** — <what's wrong and how to fix it — one or two lines>

2\. <severity emoji> **<short issue title>**
   **<subject>** — <what's wrong and how to fix it — one or two lines>
```

Rules:

- Put the severity emoji **only** on the title line (`1\. ⚠️ **…**`), never on the description line.
- The description line uses `**<subject>** —` per **Description subject** and must not contain 🚨, ⚠️, or 👀.
- **Escape the period** after the number (`1\.` not `1.`) so markdown does not collapse entries into a tight list.
- Leave an empty line between each numbered finding.

## Validation (maintainers)

Test samples live under `validation-tests/` and are excluded from normal runs via `.claude/**` in `warden.toml`.

```bash
.claude/skills/check-code-attribution/validation-tests/check-code-attribution-tests.sh
```

Expected outcomes are in `validation-tests/EXPECTED.json`. The script creates isolated git worktrees, runs Warden with `--report-on medium --json`, and asserts per-scenario pass/fail. The `missing-notices-entry` scenario uses a dedicated 2-file worktree to avoid Anthropic prompt-cache priming that suppresses the finding's severity in large concurrent batches. Exit 0 = all pass.

When manually reviewing a file under `validation-tests/scenarios/`, grep `validation-tests/THIRD_PARTY_NOTICES.catalog.md` in addition to root `THIRD_PARTY_NOTICES.md` in Quick triage step 2. See `validation-tests/README.md`.
