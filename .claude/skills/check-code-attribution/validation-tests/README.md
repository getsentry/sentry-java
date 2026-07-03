# Attribution skill validation tests

Self-contained samples for validating `check-code-attribution` without touching production SDK sources.


## Run the tests

```bash
./check-code-attribution-tests.sh
```

Requires Node.js and a Warden provider (see **Warden CLI** below).

In practice, straight command line runs tend to be a bit flakier than asking Claude Code to run the tests for you.

## Local development

### Discovering changed files

When running `/check-code-attribution` outside Warden, list files changed on the current branch vs the base branch, then apply the same exclusions as `ignorePaths` in `warden.toml`:

```bash
MB=$(git merge-base HEAD origin/main 2>/dev/null || git merge-base HEAD main)
git diff --name-only "${MB}"..HEAD
```

### Warden CLI

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

## Layout

- `EXPECTED.json` — scenario IDs and expected outcomes (single source of truth).
- `THIRD_PARTY_NOTICES.catalog.md` — NOTICES-style entries for validation class names.
- `scenarios/` — `.java` files and `THIRD_PARTY_NOTICES.mismatch-snippet.md` (copyright-mismatch fixture).
- `check-code-attribution-tests.sh` — runs Warden on a temp branch and asserts per-scenario pass/fail.
- `assert-scenarios.mjs` — validation driver (`list-isolated`, `routing-set`, `assert` subcommands); parses Warden JSONL and checks outcomes from `EXPECTED.json`.

### assert-scenarios.mjs commands

```bash
node assert-scenarios.mjs validate EXPECTED.json scenarios/     # pre-flight (no API); run automatically by the shell script
node assert-scenarios.mjs list-isolated EXPECTED.json           # id<TAB>file per isolated scenario
node assert-scenarios.mjs list-main-java EXPECTED.json scenarios/  # .java files for the main Warden batch
node assert-scenarios.mjs routing-set routing.json <id> <path>     # update id → Warden JSONL path
node assert-scenarios.mjs assert EXPECTED.json <dest-pkg> routing.json
```

Warden runs are limited to 300s. On macOS the script uses `gtimeout` (from `brew install coreutils`) when available, otherwise GNU `timeout`, otherwise `perl` with `alarm`.

## Add a scenario

1. Add `scenarios/<UniqueClassName>.java`.
2. Add or omit a catalog entry in `THIRD_PARTY_NOTICES.catalog.md`.
3. Add an entry to `EXPECTED.json`.
4. **Isolation (if needed):** If the scenario relies on a finding that could be suppressed by Anthropic prompt-cache priming when analyzed alongside many other files (e.g. a missing-NOTICES entry, or a missing header on a file that has a complete NOTICES entry), add `"isolated": true` to its `EXPECTED.json` entry. The test script creates a dedicated worktree for each isolated scenario automatically — no changes to the script itself are needed.

## Validation (maintainers)

Test samples live under `validation-tests/` and are excluded from normal skill runs via `.claude/**` in `warden.toml`.

```bash
.claude/skills/check-code-attribution/validation-tests/check-code-attribution-tests.sh
```

Expected outcomes are in `EXPECTED.json`. The script creates isolated git worktrees, runs Warden with `--report-on medium --json`, and asserts per-scenario pass/fail. Scenarios marked `"isolated": true` in `EXPECTED.json` each get their own worktree to avoid Anthropic prompt-cache priming that can suppress findings below medium in concurrent batches. Exit 0 = all pass.

When manually reviewing a file under `scenarios/`, search `THIRD_PARTY_NOTICES.catalog.md` in addition to root `THIRD_PARTY_NOTICES.md` (Quick triage step 2 in `SKILL.md`).

Non-Java fixtures required by the test script are listed in `REQUIRED_SCENARIO_FIXTURES` in `assert-scenarios.mjs`; pre-flight `validate` fails if any are missing.
