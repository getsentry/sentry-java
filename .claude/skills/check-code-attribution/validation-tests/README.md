# Attribution skill validation tests

Self-contained samples for validating `check-code-attribution` without touching production SDK sources.


## Run the tests

```bash
./check-code-attribution-tests.sh
```

Requires Node.js and an Anthropic API key (`WARDEN_ANTHROPIC_API_KEY` or `ANTHROPIC_API_KEY`). See SKILL.md "Warden CLI" section for all auth options.

In practice, straight command line runs tend to be a bit flakier than asking Claude Code to run the tests for you.

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
