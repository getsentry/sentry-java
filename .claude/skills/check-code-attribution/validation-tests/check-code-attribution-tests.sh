#!/usr/bin/env bash
# check-code-attribution-tests.sh  —  Validate the check-code-attribution skill against synthetic scenarios.
#
# Usage:
#   ./check-code-attribution-tests.sh [--help]
#
# What it does:
#   1. Validates EXPECTED.json and scenario fixtures (no API calls).
#   2. Creates an isolated git worktree on a temp branch from HEAD.
#   3. Creates a diff (non-isolated .java files, NOTICES catalog, mismatch snippet),
#      commits, and runs Warden on the main batch.
#   4. Scenarios marked "isolated" in EXPECTED.json each get their own worktree and Warden
#      run to avoid prompt-cache priming that can suppress findings in concurrent batches.
#   5. Asserts per-scenario pass/fail against EXPECTED.json (>= medium findings only).
#   6. Prints Warden's actual output for each failing scenario.
#   7. Cleans up all worktrees.
#
# Requires:
#   - Node.js / npx
#   - One of: WARDEN_ANTHROPIC_API_KEY, ANTHROPIC_API_KEY, or Pi OAuth config
#     (see SKILL.md "Warden CLI" section for setup options)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../../.." && pwd)"
SCENARIOS_DIR="$SCRIPT_DIR/scenarios"
CATALOG="$SCRIPT_DIR/THIRD_PARTY_NOTICES.catalog.md"
EXPECTED_JSON="$SCRIPT_DIR/EXPECTED.json"
VALIDATION="$SCRIPT_DIR/assert-scenarios.mjs"
MISMATCH_SNIPPET="$SCENARIOS_DIR/THIRD_PARTY_NOTICES.mismatch-snippet.md"

# Destination path inside the worktree — must not appear in warden.toml ignorePaths.
DEST_PACKAGE_PATH="sentry/src/test/java/io/sentry/skills/verification"

# Warden wall-clock limit (seconds).
TIMEOUT_SEC=300

die() { echo "ERROR: $*" >&2; exit 1; }

show_usage() {
  cat <<'EOF'
Usage: check-code-attribution-tests.sh [--help]

Validates the check-code-attribution skill against all scenarios in EXPECTED.json.
Runs Warden on a temporary branch and asserts per-scenario pass/fail (>= medium findings).

Prerequisites:
  - Node.js (npx)
  - API key: WARDEN_ANTHROPIC_API_KEY or ANTHROPIC_API_KEY
    (or Pi OAuth: npx pi && /login — see SKILL.md "Warden CLI" section)
  - Wall-clock limit: gtimeout (brew install coreutils), GNU timeout, or perl
EOF
}

[[ "${1:-}" == "--help" || "${1:-}" == "-h" ]] && { show_usage; exit 0; }

# --- prereq checks ---

command -v node >/dev/null 2>&1 || die "node not found — install Node.js."
command -v npx >/dev/null 2>&1 || die "npx not found — install Node.js."
command -v git >/dev/null 2>&1 || die "git not found."

# macOS: GNU timeout is `gtimeout` from coreutils; fall back to perl alarm.
TIMEOUT_CMD=()
if command -v gtimeout >/dev/null 2>&1; then
  TIMEOUT_CMD=(gtimeout "$TIMEOUT_SEC")
elif command -v timeout >/dev/null 2>&1; then
  TIMEOUT_CMD=(timeout "$TIMEOUT_SEC")
elif command -v perl >/dev/null 2>&1; then
  TIMEOUT_CMD=(perl -e 'alarm shift; exec @ARGV' "$TIMEOUT_SEC")
else
  die "Need gtimeout (brew install coreutils), GNU timeout, or perl for Warden wall-clock limit"
fi

if [[ -z "${WARDEN_ANTHROPIC_API_KEY:-}" && -z "${ANTHROPIC_API_KEY:-}" ]]; then
  if [[ ! -f "$HOME/.pi/agent/auth.json" ]]; then
    die "No API key found. Set WARDEN_ANTHROPIC_API_KEY, ANTHROPIC_API_KEY, or run: npx pi && /login"
  fi
fi

node "$VALIDATION" validate "$EXPECTED_JSON" "$SCENARIOS_DIR"

# --- cleanup tracking ---

declare -a WORKTREES=()
declare -a BRANCHES=()
declare -a JSON_FILES=()

cleanup() {
  for wt in "${WORKTREES[@]+"${WORKTREES[@]}"}"; do
    git -C "$REPO_ROOT" worktree remove --force "$wt" 2>/dev/null || true
  done
  for b in "${BRANCHES[@]+"${BRANCHES[@]}"}"; do
    git -C "$REPO_ROOT" branch -D "$b" 2>/dev/null || true
  done
  (( ${#JSON_FILES[@]} )) && rm -f "${JSON_FILES[@]}"
}
trap cleanup EXIT

# --- resolve base commit ---
# Branch from HEAD so the worktree includes the current skill definition.

BASE=$(git -C "$REPO_ROOT" rev-parse HEAD || die "Cannot resolve HEAD.")
TS=$(date +%s)

# --- helpers ---

# Commits paths in a validation worktree with consistent author metadata.
# Usage: git_commit_in_worktree <worktree> <message> [path...]
git_commit_in_worktree() {
  local worktree="$1" message="$2"
  shift 2
  if (($# > 0)); then
    git -C "$worktree" add "$@"
  fi
  git -C "$worktree" \
      -c user.email="ci@sentry.io" \
      -c user.name="Validation Test" \
      commit --quiet -m "$message"
}

# Creates a git worktree from $BASE and commits the NOTICES catalog as the Warden
# analysis base — so only fixture changes appear in the diff Warden analyzes.
# Prints the catalog-commit SHA to stdout.
setup_catalog_base() {
  local worktree="$1" branch="$2"
  git -C "$REPO_ROOT" worktree add --quiet "$worktree" "$BASE" -b "$branch"
  printf '\n' >> "$worktree/THIRD_PARTY_NOTICES.md"
  sed "s|validation-tests/scenarios/|${DEST_PACKAGE_PATH}/|g" \
      "$CATALOG" >> "$worktree/THIRD_PARTY_NOTICES.md"
  git_commit_in_worktree "$worktree" "test: apply NOTICES catalog [skip ci]" \
      THIRD_PARTY_NOTICES.md
  git -C "$worktree" rev-parse HEAD
}

# Appends the mismatch snippet to THIRD_PARTY_NOTICES.md, stripping the fixture's
# prose header so only the NOTICES entry itself lands in the file.
append_mismatch_snippet() {
  local worktree="$1"
  printf '\n' >> "$worktree/THIRD_PARTY_NOTICES.md"
  sed '1,/^---$/d' "$MISMATCH_SNIPPET" >> "$worktree/THIRD_PARTY_NOTICES.md"
}

# Runs Warden and writes JSON output to the given file.
run_warden() {
  local base="$1" worktree="$2" json_out="$3" label="$4"
  echo "Running Warden on ${base:0:7}..HEAD ($label)..."
  : > "$json_out"
  if ! "${TIMEOUT_CMD[@]}" npx @sentry/warden "${base}..HEAD" \
      --skill check-code-attribution \
      --fail-on off \
      --report-on medium \
      --json \
      -C "$worktree" \
      > "$json_out"; then
    if [[ ! -s "$json_out" ]]; then
      die "Warden failed for $label with no JSON output (check API key, network, and Warden logs)."
    fi
    die "Warden exited with an error for $label but left partial JSON in $json_out."
  fi
  [[ -s "$json_out" ]] || die "Warden succeeded but produced no JSON output for $label."
}

# --- main worktree: non-isolated scenarios ---
# Isolated .java files are omitted here; they get dedicated worktrees below.

echo "Creating worktrees from $(git -C "$REPO_ROOT" rev-parse --short "$BASE")..."
echo ""

MAIN_WORKTREE=$(mktemp -d)
MAIN_BRANCH="validation-main-${TS}"
MAIN_JSON=$(mktemp)
ROUTING_JSON_FILE=$(mktemp)
echo '{}' > "$ROUTING_JSON_FILE"
WORKTREES+=("$MAIN_WORKTREE")
BRANCHES+=("$MAIN_BRANCH")
JSON_FILES+=("$MAIN_JSON" "$ROUTING_JSON_FILE")

MAIN_BASE=$(setup_catalog_base "$MAIN_WORKTREE" "$MAIN_BRANCH")

DEST_DIR="$MAIN_WORKTREE/$DEST_PACKAGE_PATH"
mkdir -p "$DEST_DIR"

shopt -s nullglob
copied=0
while IFS= read -r java_file; do
  cp "$SCENARIOS_DIR/$java_file" "$DEST_DIR/"
  copied=$((copied + 1))
done < <(node "$VALIDATION" list-main-java "$EXPECTED_JSON" "$SCENARIOS_DIR")
echo "Copied ${copied} scenario files → $DEST_PACKAGE_PATH/ (non-isolated batch)"
append_mismatch_snippet "$MAIN_WORKTREE"
git_commit_in_worktree "$MAIN_WORKTREE" \
    "test: add check-code-attribution validation fixtures [skip ci]" \
    "$DEST_PACKAGE_PATH" THIRD_PARTY_NOTICES.md

run_warden "$MAIN_BASE" "$MAIN_WORKTREE" "$MAIN_JSON" "main"
node "$VALIDATION" routing-set "$ROUTING_JSON_FILE" main "$MAIN_JSON"

# --- isolated worktrees: one per scenario marked "isolated" in EXPECTED.json ---
#
# Scenarios where Anthropic prompt-cache priming can suppress findings in a concurrent
# batch get their own worktree and Warden run. EXPECTED.json is the single source of
# truth for which scenarios need isolation — add "isolated": true there, not here.
# Java isolates omit the mismatch snippet; the NOTICES mismatch scenario adds it alone.

while IFS=$'\t' read -r id file; do
  worktree=$(mktemp -d)
  branch="validation-isolated-${TS}-${id//[^a-zA-Z0-9]/-}"
  json=$(mktemp)
  WORKTREES+=("$worktree")
  BRANCHES+=("$branch")
  JSON_FILES+=("$json")

  base=$(setup_catalog_base "$worktree" "$branch")

  commit_paths=()
  if [[ "$file" == *.java ]]; then
    dest_dir="$worktree/$DEST_PACKAGE_PATH"
    mkdir -p "$dest_dir"
    cp "$SCENARIOS_DIR/$file" "$dest_dir/"
    commit_paths=("$DEST_PACKAGE_PATH")
  elif [[ "$file" == "THIRD_PARTY_NOTICES.md" ]]; then
    append_mismatch_snippet "$worktree"
    commit_paths=(THIRD_PARTY_NOTICES.md)
  else
    die "Unsupported isolated scenario file: $file (id: $id)"
  fi

  git_commit_in_worktree "$worktree" "test: isolated fixture for $id [skip ci]" \
      "${commit_paths[@]}"

  echo ""
  run_warden "$base" "$worktree" "$json" "$id"
  node "$VALIDATION" routing-set "$ROUTING_JSON_FILE" "$id" "$json"

done < <(node "$VALIDATION" list-isolated "$EXPECTED_JSON")

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# --- assert per-scenario ---
#
# ROUTING_JSON_FILE maps scenario id → Warden JSONL path; non-isolated scenarios use "main".

node "$VALIDATION" assert "$EXPECTED_JSON" "$DEST_PACKAGE_PATH" "$ROUTING_JSON_FILE"
