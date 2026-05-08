#!/usr/bin/env bash
# check-code-attribution-tests.sh  —  Validate the check-code-attribution skill against synthetic scenarios.
#
# Usage:
#   ./check-code-attribution-tests.sh [--help]
#
# What it does:
#   1. Creates an isolated git worktree on a temp branch from origin/main (or main).
#   2. Copies scenario .java files to a non-ignored source path, appends
#      THIRD_PARTY_NOTICES.catalog.md + the mismatch fixture entry to THIRD_PARTY_NOTICES.md.
#   3. Commits and runs: npx @sentry/warden <base>..HEAD --skill check-code-attribution
#   4. Asserts per-scenario pass/fail against EXPECTED.json (medium+ findings only).
#   5. On failure, prints Warden's actual output for each failing scenario.
#   6. Cleans up the worktree.
#
# Requires:
#   - Node.js / npx
#   - One of: WARDEN_ANTHROPIC_API_KEY, ANTHROPIC_API_KEY, or Pi OAuth config
#     (see SKILL.md "Warden CLI" section for setup options)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../../.." && pwd)"
TESTS_DIR="$SCRIPT_DIR"
SCENARIOS_DIR="$TESTS_DIR/scenarios"
CATALOG="$TESTS_DIR/THIRD_PARTY_NOTICES.catalog.md"
EXPECTED_JSON="$TESTS_DIR/EXPECTED.json"

# Destination path inside the worktree — not in any warden.toml ignorePaths.
DEST_PACKAGE_PATH="sentry/src/test/java/io/sentry/skills/verification"

die() { echo "ERROR: $*" >&2; exit 1; }

# Adds a git worktree branched from $BASE and commits the NOTICES catalog as
# the Warden analysis base. Prints the resulting base SHA to stdout.
# Usage: WARDEN_BASE=$(setup_catalog_base <worktree_path> <branch_name>)
setup_catalog_base() {
  local worktree="$1" branch="$2"
  git -C "$REPO_ROOT" worktree add --quiet "$worktree" "$BASE" -b "$branch"
  printf '\n' >> "$worktree/THIRD_PARTY_NOTICES.md"
  sed "s|validation-tests/scenarios/|${DEST_PACKAGE_PATH}/|g" \
      "$CATALOG" >> "$worktree/THIRD_PARTY_NOTICES.md"
  git -C "$worktree" add THIRD_PARTY_NOTICES.md
  git -C "$worktree" \
      -c user.email="ci@sentry.io" \
      -c user.name="Validation Test" \
      commit --quiet -m "test: apply NOTICES catalog [skip ci]"
  git -C "$worktree" rev-parse HEAD
}

show_usage() {
  cat <<EOF
Usage: check-code-attribution-tests.sh [--help]

Validates the check-code-attribution skill against all scenarios in EXPECTED.json.
Runs Warden on a temporary branch and asserts per-scenario pass/fail (medium+ findings).

Prerequisites:
  - Node.js (npx)
  - API key: WARDEN_ANTHROPIC_API_KEY or ANTHROPIC_API_KEY
    (or Pi OAuth: npx pi && /login — see SKILL.md "Warden CLI" section)
EOF
}

[[ "${1:-}" == "--help" || "${1:-}" == "-h" ]] && { show_usage; exit 0; }

# --- prereq checks -------------------------------------------------------

command -v npx >/dev/null 2>&1 || die "npx not found — install Node.js."
command -v git >/dev/null 2>&1 || die "git not found."

# Use GNU timeout if available (coreutils); fall back to no timeout on macOS without coreutils.
TIMEOUT_CMD=""
command -v timeout >/dev/null 2>&1 && TIMEOUT_CMD="timeout 300"

if [[ -z "${WARDEN_ANTHROPIC_API_KEY:-}" && -z "${ANTHROPIC_API_KEY:-}" ]]; then
  if [[ ! -f "$HOME/.pi/agent/auth.json" ]]; then
    echo "WARNING: No API key set. Set WARDEN_ANTHROPIC_API_KEY, ANTHROPIC_API_KEY, or: npx pi && /login"
    echo ""
  fi
fi

# --- resolve base commit --------------------------------------------------
# Branch from HEAD so the worktree includes the skill definition. The test
# commit (fixture files) is added on top, and Warden runs on BASE..HEAD.

BASE=$(git -C "$REPO_ROOT" rev-parse HEAD \
    || die "Cannot resolve HEAD.")

# --- create isolated worktree --------------------------------------------

TS=$(date +%s)
TEMP_WORKTREE=$(mktemp -d)
TEMP_BRANCH="validation-test-${TS}"
TEMP_WORKTREE2=$(mktemp -d)
TEMP_BRANCH2="validation-isolated-${TS}"
TEMP_WORKTREE3=$(mktemp -d)
TEMP_BRANCH3="validation-isolated2-${TS}"
TEMP_WORKTREE4=$(mktemp -d)
TEMP_BRANCH4="validation-isolated3-${TS}"
WARDEN_JSON_FILE=$(mktemp)
WARDEN_JSON_FILE2=$(mktemp)
WARDEN_JSON_FILE3=$(mktemp)
WARDEN_JSON_FILE4=$(mktemp)

cleanup() {
  git -C "$REPO_ROOT" worktree remove --force "$TEMP_WORKTREE" 2>/dev/null || true
  git -C "$REPO_ROOT" branch -D "$TEMP_BRANCH" 2>/dev/null || true
  git -C "$REPO_ROOT" worktree remove --force "$TEMP_WORKTREE2" 2>/dev/null || true
  git -C "$REPO_ROOT" branch -D "$TEMP_BRANCH2" 2>/dev/null || true
  git -C "$REPO_ROOT" worktree remove --force "$TEMP_WORKTREE3" 2>/dev/null || true
  git -C "$REPO_ROOT" branch -D "$TEMP_BRANCH3" 2>/dev/null || true
  git -C "$REPO_ROOT" worktree remove --force "$TEMP_WORKTREE4" 2>/dev/null || true
  git -C "$REPO_ROOT" branch -D "$TEMP_BRANCH4" 2>/dev/null || true
  rm -f "$WARDEN_JSON_FILE" "$WARDEN_JSON_FILE2" "$WARDEN_JSON_FILE3" "$WARDEN_JSON_FILE4"
}
trap cleanup EXIT

# --- commit 1: catalog only (forms the Warden base) ---------------------
#
# By landing catalog NOTICES entries before the scenario Java files, the diff
# that Warden analyzes contains only the Java additions + the mismatch entry.
# This prevents the LLM from reasoning that "NOTICES is being updated for all
# these files in the same PR" — the catalog is already on the base branch.

echo "Creating worktrees from $(git -C "$REPO_ROOT" rev-parse --short "$BASE")..."
WARDEN_BASE=$(setup_catalog_base "$TEMP_WORKTREE" "$TEMP_BRANCH")

# --- commit 2: scenario files + mismatch entry (what Warden analyzes) ---

DEST_DIR="$TEMP_WORKTREE/$DEST_PACKAGE_PATH"
mkdir -p "$DEST_DIR"
cp "$SCENARIOS_DIR/"*.java "$DEST_DIR/"
echo "Copied $(ls "$DEST_DIR"/*.java | wc -l | tr -d ' ') scenario files → $DEST_PACKAGE_PATH/"

printf '\n' >> "$TEMP_WORKTREE/THIRD_PARTY_NOTICES.md"
sed '1,/^---$/d' "$SCENARIOS_DIR/THIRD_PARTY_NOTICES.mismatch-snippet.md" \
    >> "$TEMP_WORKTREE/THIRD_PARTY_NOTICES.md"

git -C "$TEMP_WORKTREE" add "$DEST_PACKAGE_PATH" THIRD_PARTY_NOTICES.md
git -C "$TEMP_WORKTREE" \
    -c user.email="ci@sentry.io" \
    -c user.name="Validation Test" \
    commit --quiet -m "test: add check-code-attribution validation fixtures [skip ci]"

echo "Committed. Running Warden on ${WARDEN_BASE:0:7}..HEAD"
echo ""

# --- isolated worktree: HeaderCompleteButNoticeMissing only ------------
# When analyzed concurrently with many "no-finding" files, the LLM can
# classify the missing-NOTICES finding below medium due to Anthropic prompt-
# cache priming. An isolated 2-file worktree gives reliable medium classification.

WARDEN_BASE2=$(setup_catalog_base "$TEMP_WORKTREE2" "$TEMP_BRANCH2")

DEST_DIR2="$TEMP_WORKTREE2/$DEST_PACKAGE_PATH"
mkdir -p "$DEST_DIR2"
cp "$SCENARIOS_DIR/HeaderCompleteButNoticeMissing.java" "$DEST_DIR2/"
printf '\n' >> "$TEMP_WORKTREE2/THIRD_PARTY_NOTICES.md"
sed '1,/^---$/d' "$SCENARIOS_DIR/THIRD_PARTY_NOTICES.mismatch-snippet.md" \
    >> "$TEMP_WORKTREE2/THIRD_PARTY_NOTICES.md"
git -C "$TEMP_WORKTREE2" add "$DEST_PACKAGE_PATH" THIRD_PARTY_NOTICES.md
git -C "$TEMP_WORKTREE2" \
    -c user.email="ci@sentry.io" \
    -c user.name="Validation Test" \
    commit --quiet -m "test: add HeaderCompleteButNoticeMissing only [skip ci]"

# --- isolated worktree: HeaderMissingButNoticePresent only ----------
# Same cache-priming issue as HeaderCompleteButNoticeMissing: a complete
# NOTICES entry suppresses the missing-header finding in a concurrent batch.

WARDEN_BASE3=$(setup_catalog_base "$TEMP_WORKTREE3" "$TEMP_BRANCH3")

DEST_DIR3="$TEMP_WORKTREE3/$DEST_PACKAGE_PATH"
mkdir -p "$DEST_DIR3"
cp "$SCENARIOS_DIR/HeaderMissingButNoticePresent.java" "$DEST_DIR3/"
printf '\n' >> "$TEMP_WORKTREE3/THIRD_PARTY_NOTICES.md"
sed '1,/^---$/d' "$SCENARIOS_DIR/THIRD_PARTY_NOTICES.mismatch-snippet.md" \
    >> "$TEMP_WORKTREE3/THIRD_PARTY_NOTICES.md"
git -C "$TEMP_WORKTREE3" add "$DEST_PACKAGE_PATH" THIRD_PARTY_NOTICES.md
git -C "$TEMP_WORKTREE3" \
    -c user.email="ci@sentry.io" \
    -c user.name="Validation Test" \
    commit --quiet -m "test: add HeaderMissingButNoticePresent only [skip ci]"

# --- isolated worktree: header-vs-notice-mismatch only ------------------
# Isolate so the mismatch finding is independently asserted without interference
# from other NOTICES findings in a joint run.

WARDEN_BASE4=$(setup_catalog_base "$TEMP_WORKTREE4" "$TEMP_BRANCH4")

printf '\n' >> "$TEMP_WORKTREE4/THIRD_PARTY_NOTICES.md"
sed '1,/^---$/d' "$SCENARIOS_DIR/THIRD_PARTY_NOTICES.mismatch-snippet.md" \
    >> "$TEMP_WORKTREE4/THIRD_PARTY_NOTICES.md"
git -C "$TEMP_WORKTREE4" add THIRD_PARTY_NOTICES.md
git -C "$TEMP_WORKTREE4" \
    -c user.email="ci@sentry.io" \
    -c user.name="Validation Test" \
    commit --quiet -m "test: add MismatchLib NOTICES entry only [skip ci]"

# --- run warden (main: all files) ----------------------------------------
# stderr (Warden progress) flows to the terminal; stdout (JSON) captured to file.

$TIMEOUT_CMD npx @sentry/warden "${WARDEN_BASE}..HEAD" \
    --skill check-code-attribution \
    --report-on medium \
    --json \
    -C "$TEMP_WORKTREE" \
    > "$WARDEN_JSON_FILE" || true

echo ""
echo "Running isolated check for header-complete-but-notice-missing (${WARDEN_BASE2:0:7}..HEAD)..."
$TIMEOUT_CMD npx @sentry/warden "${WARDEN_BASE2}..HEAD" \
    --skill check-code-attribution \
    --report-on medium \
    --json \
    -C "$TEMP_WORKTREE2" \
    > "$WARDEN_JSON_FILE2" || true

echo ""
echo "Running isolated check for header-missing-but-notice-present (${WARDEN_BASE3:0:7}..HEAD)..."
$TIMEOUT_CMD npx @sentry/warden "${WARDEN_BASE3}..HEAD" \
    --skill check-code-attribution \
    --report-on medium \
    --json \
    -C "$TEMP_WORKTREE3" \
    > "$WARDEN_JSON_FILE3" || true

echo ""
echo "Running isolated check for header-vs-notice-mismatch (${WARDEN_BASE4:0:7}..HEAD)..."
$TIMEOUT_CMD npx @sentry/warden "${WARDEN_BASE4}..HEAD" \
    --skill check-code-attribution \
    --report-on medium \
    --json \
    -C "$TEMP_WORKTREE4" \
    > "$WARDEN_JSON_FILE4" || true

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# --- assert per-scenario -------------------------------------------------

node - "$EXPECTED_JSON" "$WARDEN_JSON_FILE" "$WARDEN_JSON_FILE2" "$WARDEN_JSON_FILE3" "$WARDEN_JSON_FILE4" "$DEST_PACKAGE_PATH" <<'NODEJS'
const fs = require('fs');
const [,, expectedPath, wardenPath, wardenPath2, wardenPath3, wardenPath4, destPkg] = process.argv;

const scenarios = JSON.parse(fs.readFileSync(expectedPath, 'utf8'));

function parseWardenJsonl(path) {
  const fileMap = {};
  const allFindings = [];
  try {
    const raw = fs.readFileSync(path, 'utf8').trim();
    if (!raw) return { files: [], findings: [] };
    const records = raw.split('\n').filter(l => l.trim()).map(l => JSON.parse(l));
    for (const record of records) {
      const file = record.chunk && record.chunk.file;
      if (!file) continue;
      const recordFindings = record.findings || [];
      fileMap[file] = (fileMap[file] || 0) + recordFindings.length;
      for (const f of recordFindings) {
        allFindings.push({ ...f, location: f.location || { path: file, startLine: 1 } });
      }
    }
  } catch (e) {
    console.error('ERROR: Could not parse Warden output from ' + path + ':', e.message);
    process.exit(2);
  }
  return {
    files: Object.entries(fileMap).map(([filename, findings]) => ({ filename, findings })),
    findings: allFindings,
  };
}

// Warden --json outputs JSONL: one record per file chunk.
// Main run covers all files. Isolated runs cover scenarios where prompt-cache priming
// or finding co-location would prevent independent per-scenario assertion.
const warden = parseWardenJsonl(wardenPath);
const wardenIsolated2 = parseWardenJsonl(wardenPath2);
const wardenIsolated3 = parseWardenJsonl(wardenPath3);
const wardenIsolated4 = parseWardenJsonl(wardenPath4);

const GREEN = '\x1b[32m';
const RED   = '\x1b[31m';
const RESET = '\x1b[0m';

const failures = [];
let pass = 0;

for (const s of scenarios) {
  const wardenFile = s.file === 'THIRD_PARTY_NOTICES.md'
    ? 'THIRD_PARTY_NOTICES.md'
    : `${destPkg}/${s.file}`;

  // Route each scenario to its source run. Cache-priming and co-location issues
  // require isolated runs so assertions are truly independent.
  let source = warden;
  if (s.id === 'header-complete-but-notice-missing') source = wardenIsolated2;
  if (s.id === 'header-missing-but-notice-present') source = wardenIsolated3;
  if (s.id === 'header-vs-notice-mismatch') source = wardenIsolated4;
  const fileReport = (source.files || []).find(f => f.filename === wardenFile);
  const count = fileReport ? fileReport.findings : 0;
  const passed = s.expectFinding ? count > 0 : count === 0;

  if (passed) {
    console.log(`${GREEN}PASS${RESET} ${s.id}`);
    pass++;
  } else {
    const reason = s.expectFinding
      ? 'expected finding (medium+), got none'
      : `expected no finding (medium+), got ${count}`;
    console.log(`${RED}FAIL${RESET} ${s.id}  (${reason})`);

    const fileFindings = (source.findings || []).filter(
      f => f.location && f.location.path === wardenFile
    );
    failures.push({ id: s.id, findings: fileFindings });
  }
}

const total = scenarios.length;
console.log('');
console.log(`${total} scenarios: ${pass} passed, ${total - pass} failed`);

if (failures.length > 0) {
  console.log('');
  console.log('Warden output');
  console.log('══════════════════════');

  for (const { id, findings } of failures) {
    console.log('');
    console.log(id);
    console.log('-'.repeat(id.length));
    if (findings.length === 0) {
      console.log('(Warden produced no findings for this file)');
    } else {
      for (const f of findings) {
        console.log(f.title);
        if (f.description) console.log(f.description);
        if (f.verification) console.log('\nVerification: ' + f.verification);
        console.log('');
      }
    }
  }

  process.exit(1);
}
NODEJS
