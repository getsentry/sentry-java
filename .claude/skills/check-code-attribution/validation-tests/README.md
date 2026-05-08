# Attribution skill validation tests

Self-contained samples for validating `check-code-attribution` without touching production SDK sources.


## Run the tests

```bash
./check-code-attribution-tests.sh
```

Requires Node.js and an Anthropic API key (`WARDEN_ANTHROPIC_API_KEY` or `ANTHROPIC_API_KEY`). See SKILL.md "Warden CLI" section for all auth options.

In practice, local runs tend to be a bit flakier than runs made via Claude Code (i.e., asking Claude to run the tests for you).

## Layout

- `EXPECTED.json` — scenario IDs and expected outcomes (single source of truth).
- `THIRD_PARTY_NOTICES.catalog.md` — NOTICES-style entries for validation class names.
- `scenarios/` — `.java` files and `THIRD_PARTY_NOTICES.mismatch-snippet.md` (copyright-mismatch fixture).
- `check-code-attribution-tests.sh` — runs Warden on a temp branch and asserts per-scenario pass/fail.

## Add a scenario

1. Add `scenarios/<UniqueClassName>.java`.
2. Add or omit a catalog entry in `THIRD_PARTY_NOTICES.catalog.md`.
3. Add an entry to `EXPECTED.json`.
4. **Isolation (if needed):** If the scenario relies on a finding that could be suppressed by Anthropic prompt-cache priming when analyzed alongside many other files (e.g. a missing-NOTICES entry, or a missing header on a file that has a complete NOTICES entry), add a new isolated worktree run in `check-code-attribution-tests.sh` and route the scenario ID to it in the Node.js assertion block. See the existing `TEMP_WORKTREE2`/`TEMP_WORKTREE3` patterns for reference. Scenarios that do not need isolation can use the main `TEMP_WORKTREE` run (the default).
