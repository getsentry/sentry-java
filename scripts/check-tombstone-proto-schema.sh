#!/usr/bin/env bash
set -Eeuo pipefail

TRACKED_COMMIT="981d145117e8992842cdddee555c57e60c7a220a"
REMOTE_URL='https://android.googlesource.com/platform/system/core'
REMOTE_BRANCH='main'
PROTO_PATH='debuggerd/proto/tombstone.proto'
GITILES_REF="refs/heads/${REMOTE_BRANCH}"
GITILES_LOG_URL="${REMOTE_URL}/+log/${GITILES_REF}/${PROTO_PATH}?format=JSON"

MODE=auto
case "${1:-}" in
  "")
    ;;
  --git-only)
    MODE=git
    ;;
  --gitiles-only)
    MODE=gitiles
    ;;
  *)
    echo "Usage: $0 [--git-only|--gitiles-only]" >&2
    exit 2
    ;;
esac

TEMP_FILES=()
TEMP_DIRS=()
LATEST_COMMIT=""

error() {
  echo "ERROR: $*" >&2
}

show_output() {
  local label=$1
  local file=$2

  if [ -s "$file" ]; then
    echo "$label:" >&2
    sed 's/^/  /' "$file" >&2
  fi
}

require_command() {
  local command_name=$1

  if ! command -v "$command_name" >/dev/null 2>&1; then
    error "Required command not found: $command_name"
    return 1
  fi
}

make_temp_file() {
  local file
  file=$(mktemp)
  TEMP_FILES+=("$file")
  printf '%s\n' "$file"
}

make_temp_dir() {
  local dir
  dir=$(mktemp -d)
  TEMP_DIRS+=("$dir")
  printf '%s\n' "$dir"
}

cleanup() {
  local path

  for path in "${TEMP_FILES[@]}"; do
    rm -f "$path"
  done

  for path in "${TEMP_DIRS[@]}"; do
    rm -rf "$path"
  done
}

handle_unexpected_error() {
  local exit_code=$?
  error "Unexpected failure at line $1 while running: $2 (exit $exit_code)"
  exit "$exit_code"
}

trap 'handle_unexpected_error "$LINENO" "$BASH_COMMAND"' ERR
trap cleanup EXIT

run_gitiles_check() {
  local response_file
  local stderr_file
  local status

  require_command curl || return 1
  require_command jq || return 1

  response_file=$(make_temp_file)
  stderr_file=$(make_temp_file)

  if curl -fsS "$GITILES_LOG_URL" -o "$response_file" 2>"$stderr_file"; then
    :
  else
    status=$?
    error "Failed to fetch Gitiles history from:"
    error "  $GITILES_LOG_URL"
    error "curl exited with status $status."
    show_output "curl output" "$stderr_file"
    return 1
  fi

  if LATEST_COMMIT=$(tail -n +2 "$response_file" | jq -er '.log[0].commit' 2>"$stderr_file"); then
    :
  else
    status=$?
    error "Failed to parse the latest commit from the Gitiles response."
    error "jq exited with status $status."
    show_output "jq output" "$stderr_file"
    echo "Response preview:" >&2
    head -n 20 "$response_file" >&2
    return 1
  fi

  if [ -z "$LATEST_COMMIT" ]; then
    error "Gitiles response did not contain a commit hash."
    echo "Response preview:" >&2
    head -n 20 "$response_file" >&2
    return 1
  fi
}

run_git_check() {
  local repo_dir
  local stderr_file
  local status

  require_command git || return 1

  repo_dir=$(make_temp_dir)
  stderr_file=$(make_temp_file)

  if GIT_TERMINAL_PROMPT=0 git clone \
    --quiet \
    --filter=blob:none \
    --single-branch \
    --branch "$REMOTE_BRANCH" \
    --no-checkout \
    "$REMOTE_URL" "$repo_dir" 2>"$stderr_file"; then
    :
  else
    status=$?
    error "Failed to clone $REMOTE_BRANCH from:"
    error "  $REMOTE_URL"
    error "git clone exited with status $status."
    show_output "git clone output" "$stderr_file"
    return 1
  fi

  if LATEST_COMMIT=$(git -C "$repo_dir" log -n 1 --format=%H HEAD -- "$PROTO_PATH" 2>"$stderr_file"); then
    :
  else
    status=$?
    error "Failed to determine the latest commit that modified:"
    error "  $PROTO_PATH"
    error "git log exited with status $status."
    show_output "git log output" "$stderr_file"
    return 1
  fi

  if [ -z "$LATEST_COMMIT" ]; then
    error "Git history did not contain a commit for:"
    error "  $PROTO_PATH"
    return 1
  fi
}

report_result() {
  echo "Tracked commit: $TRACKED_COMMIT"
  echo "Latest commit:  $LATEST_COMMIT"

  if [ "$LATEST_COMMIT" != "$TRACKED_COMMIT" ]; then
    echo "Schema has been updated! Latest: ${REMOTE_URL}/+/${LATEST_COMMIT}/${PROTO_PATH}"
    exit 1
  fi

  echo "Schema is up to date."
}

case "$MODE" in
  auto)
    if run_gitiles_check; then
      report_result
      exit 0
    fi

    echo "Falling back to git-based check." >&2
    if run_git_check; then
      report_result
      exit 0
    fi

    exit 1
    ;;
  gitiles)
    if run_gitiles_check; then
      report_result
      exit 0
    fi

    exit 1
    ;;
  git)
    if run_git_check; then
      report_result
      exit 0
    fi

    exit 1
    ;;
esac
