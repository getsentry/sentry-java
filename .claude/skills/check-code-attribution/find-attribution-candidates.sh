#!/usr/bin/env bash
# shellcheck shell=bash
#
# Script for finding files in the current branch diff that may have added, modified, or removed an
# attribution for vendored code (those files are referred to as "attribution candidates"). This
# script handles cheap, deterministic identification and filtering; all interpretation (cross-
# referencing with THIRD_PARTY_NOTICES.md, license classification, etc.) is left to the LLM.
#
# Every run prints global metadata first (two lines), then zero or more candidate blocks:
#
#   notices_file_exists: true|false
#   notices_file_changed: true|false
#   ---
#   file: <path_to_candidate_file>
#   status: A|M|D|R (A = "added", M = "modified", D = "deleted", R = "renamed")
#   reasons: <comma-separated list of why this file was flagged as a candidate>
#   ---
#
# Exit code 0 = no file candidates and THIRD_PARTY_NOTICES.md unchanged vs merge-base.
# Exit code 10 = one or more file candidates and/or THIRD_PARTY_NOTICES.md changed (including
# NOTICES-only edits with zero candidate blocks when diff hunks do not match attribution patterns).

set -euo pipefail

MERGE_BASE=$(git merge-base HEAD origin/main 2>/dev/null || git merge-base HEAD main 2>/dev/null) || {
  echo "Error: could not determine merge-base. Neither 'origin/main' nor 'main' is reachable from HEAD." >&2
  echo "If this is a shallow clone, try: git fetch --unshallow origin main" >&2
  exit 2
}
NOTICES_FILE="THIRD_PARTY_NOTICES.md"
notices_file_exists="true"
[[ ! -f "$NOTICES_FILE" ]] && notices_file_exists="false"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
EXCLUSIONS_FILE="$SCRIPT_DIR/generated-code-exclusions.txt"

# --- Filter terms ---
# Maintainer-friendly lists of terms used to identify vendored/attributed code.
# Update these when new attribution patterns or license types are encountered.

# Strong indicators that code was adapted/copied from an external source
VENDORING_MARKERS='adapted from|backported from|copied from|derived from|ported from|translated from|vendored'

# Recognized open-source license names (regex alternation)
LICENSE_NAMES='Apache 2\.0|Apache License|BSD [0-9]|BSD License|CC-BY|Creative Commons|Eclipse Public License|EPL|GNU General Public|GPL|ISC License|LGPL|MIT License|Mozilla Public|Public Domain|SPDX-License-Identifier|Unlicense'

# Combined pattern for diff-hunk scanning of modified files. Intentionally broader
# than VENDORING_MARKERS: it includes generic terms ("copyright", "licensed") so the
# diff-hunk scan catches any attribution-related change. False positives from first-
# party headers are filtered out downstream by has_third_party_attribution() checks
# against the full file content (for additions) or merge-base content (for removals).
ATTRIBUTION_PATTERN="$VENDORING_MARKERS|copyright|licensed|$LICENSE_NAMES"

# Sentry entity names — copyright lines mentioning these are treated as first-party
SENTRY_ENTITIES='functional software|getsentry|sentry software'

# Build sed expression from SENTRY_ENTITIES (keeps entity list and strip patterns in sync)
_sentry_sed=""
IFS='|' read -ra _sentry_parts <<< "$SENTRY_ENTITIES"
for _part in "${_sentry_parts[@]}"; do
  _sentry_sed+="s/${_part}//g; "
done
SENTRY_STRIP_SED="${_sentry_sed}s/sentry//g; s/copyright//g; s/(c)//g"
unset _sentry_sed _sentry_parts _part

# Path segments that suggest vendored/external code
VENDOR_PATH_MARKERS='external|shaded|third-party|third_party|thirdparty|vendor|vendored'

is_binary_file() {
  # -I treats binary files as non-matching → exit 1; empty pattern matches any text file → exit 0
  ! grep -Iq '' "$1" 2>/dev/null
}

# Infrastructure/config directories that never contain vendored source code.
# For generated-file filename patterns, see generated-code-exclusions.txt.
is_excluded_path() {
  [[ "$1" =~ ^\.claude/ || "$1" =~ ^\.github/ || "$1" =~ ^\.gradle/ || "$1" =~ ^\.idea/ || "$1" =~ ^\.mvn/ || "$1" =~ ^buildSrc/ || "$1" =~ ^build-logic/ || "$1" =~ ^gradle/ ]]
}

# Load exclusion patterns once into a temp file. Each pattern is validated before inclusion:
# invalid regexes are skipped with a warning, and patterns over 200 chars are rejected to
# limit ReDoS surface (the file is repo-controlled, but validation catches accidental breakage).
WORK_DIR=$(mktemp -d)
trap 'rm -rf "$WORK_DIR"' EXIT
EXCLUSION_PATTERNS_FILE="$WORK_DIR/exclusions"
if [[ -f "$EXCLUSIONS_FILE" ]]; then
  while IFS= read -r pattern; do
    [[ -z "$pattern" || "$pattern" == \#* ]] && continue
    if [[ ${#pattern} -gt 200 ]]; then
      echo "Warning: skipping exclusion pattern exceeding 200 chars: ${pattern:0:40}..." >&2
      continue
    fi
    rc=0; printf '' | grep -qE -- "$pattern" 2>/dev/null || rc=$?
    if [[ $rc -eq 2 ]]; then
      echo "Warning: skipping invalid regex in exclusions: $pattern" >&2
      continue
    fi
    printf '%s\n' "$pattern"
  done < "$EXCLUSIONS_FILE" > "$EXCLUSION_PATTERNS_FILE"
fi

is_generated_file() {
  [[ -s "$EXCLUSION_PATTERNS_FILE" ]] && grep -qE -f "$EXCLUSION_PATTERNS_FILE" <<< "$1"
}

has_vendor_path() {
  local path_lower
  path_lower=$(echo "$1" | tr '[:upper:]' '[:lower:]')
  [[ "$path_lower" =~ (^|/)($VENDOR_PATH_MARKERS)(/|$) ]]
}

has_third_party_attribution() {
  local filepath="$1"

  # Vendoring markers are strong standalone indicators
  grep -qiE "$VENDORING_MARKERS" "$filepath" && return 0

  local copyright_lines
  copyright_lines=$(grep -iE 'copyright' "$filepath" 2>/dev/null) || true
  [[ -z "$copyright_lines" ]] && return 1

  # Fast path: any copyright line without a Sentry entity is definitively third-party
  echo "$copyright_lines" | grep -qivE "$SENTRY_ENTITIES" && return 0

  # Slow path: all copyright lines mention a Sentry entity. Check for dual-copyright
  # lines (e.g., "Copyright Functional Software and Example Corp") by stripping Sentry
  # names and common boilerplate, then looking for remaining substantive words.
  echo "$copyright_lines" | \
    tr '[:upper:]' '[:lower:]' | \
    sed "$SENTRY_STRIP_SED" | \
    sed 's/[0-9]//g; s/[^a-z]/ /g' | \
    tr -s ' ' '\n' | \
    grep -vxE '(and|the|inc|llc|ltd|or|of|by|all|rights|reserved)' | \
    grep -qE '[a-z]{3,}' && return 0

  # License keywords alone (without a non-Sentry copyright or vendoring marker) do NOT
  # indicate vendored code — many first-party files carry the project's own license header.

  return 1
}

# Check if diff hunks contain added attribution-related lines
has_added_attribution_lines() {
  local diff_output="$1"
  grep -E '^\+' <<< "$diff_output" | grep -vE '^\+\+\+ (b/|/dev/null)' | grep -qiE "$ATTRIBUTION_PATTERN"
}

# Check if diff hunks contain removed attribution-related lines
has_removed_attribution_lines() {
  local diff_output="$1"
  grep -E '^-' <<< "$diff_output" | grep -vE '^--- (a/|/dev/null)' | grep -qiE "$ATTRIBUTION_PATTERN"
}

# Collect changed files from all sources (committed, staged, unstaged, untracked),
# deduplicated by current filepath (last occurrence wins). Sources are listed oldest
# to newest, so the most recent state takes precedence — e.g. a file committed as
# "M" (modified) then staged for deletion resolves to "D".
collect_changed_files() {
  {
    git diff "$MERGE_BASE"..HEAD --name-status --find-renames 2>/dev/null || true
    git diff --cached --name-status --find-renames 2>/dev/null || true
    git diff --name-status --find-renames 2>/dev/null || true
    git ls-files --others --exclude-standard 2>/dev/null | while IFS= read -r path; do
      [[ -n "$path" ]] && printf 'A\t%s\n' "$path"
    done
  } | awk -F'\t' '{
    if ($1 ~ /^R/ && NF >= 3) key = $3
    else key = $NF
    data[key] = $0
    if (!seen[key]++) order[++n] = key
  }
  END {
    for (i = 1; i <= n; i++) print data[order[i]]
  }'
}

# Diff from merge-base to the current worktree state (committed + staged + unstaged)
# in a single pass. No duplicate hunks because git diffs the merge-base blob
# directly against the current worktree file.
combined_diff() {
  local filepath="$1"
  git diff "$MERGE_BASE" -- "$filepath" 2>/dev/null || echo "Warning: git diff failed for $filepath" >&2
}

# Check if THIRD_PARTY_NOTICES.md was modified in this branch (committed, staged, or unstaged)
notices_file_changed="false"
if git diff "$MERGE_BASE"..HEAD --name-only -- "$NOTICES_FILE" 2>/dev/null | grep -q . \
  || git diff --cached --name-only -- "$NOTICES_FILE" 2>/dev/null | grep -q . \
  || git diff --name-only -- "$NOTICES_FILE" 2>/dev/null | grep -q .; then
  notices_file_changed="true"
fi

# Global metadata is always printed first so consumers can run NOTICES review even when there are
# zero file candidates (e.g. Scope-only edits to THIRD_PARTY_NOTICES.md).
echo "notices_file_exists: $notices_file_exists"
echo "notices_file_changed: $notices_file_changed"

found_any=false

# Process each changed file
while IFS=$'\t' read -r status filepath old_filepath; do
  [[ -z "$status" ]] && continue

  status_char="${status:0:1}"

  # For renames, `read` assigns: status=R###, filepath=old_path, old_filepath=new_path.
  # Swap so filepath holds the current (new) path and old_filepath holds the original.
  if [[ "$status_char" == "R" ]]; then
    current_path="$old_filepath"
    old_filepath="$filepath"
    filepath="$current_path"
  fi

  # NOTICES changes are tracked via the notices_file_changed metadata field, not as a candidate.
  [[ "$filepath" == "$NOTICES_FILE" ]] && continue
  is_excluded_path "$filepath" && continue
  is_generated_file "$filepath" && continue

  # Skip binary files
  if [[ "$status_char" != "D" ]] && is_binary_file "$filepath"; then
    continue
  fi
  is_candidate=false
  reasons=()

  # --- Determine content and candidate status ---

  if [[ "$status_char" == "A" ]]; then
    # Cap at 100KB to avoid overhead on large generated files
    if [[ $(wc -c < "$filepath" 2>/dev/null) -gt 102400 ]]; then
      echo "Warning: $filepath exceeds 100KB — only the first 100KB will be scanned for attribution markers." >&2
    fi
    head -c 102400 "$filepath" > "$WORK_DIR/content" 2>/dev/null || continue

    if has_vendor_path "$filepath"; then
      is_candidate=true
      reasons+=("path suggests vendored code")
    fi

    if has_third_party_attribution "$WORK_DIR/content"; then
      is_candidate=true
      reasons+=("attribution markers in file")
    fi

  elif [[ "$status_char" == "D" ]]; then
    git show "$MERGE_BASE":"$filepath" > "$WORK_DIR/content" 2>/dev/null || continue

    if has_vendor_path "$filepath"; then
      is_candidate=true
      reasons+=("deleted file in vendor path")
    fi

    if has_third_party_attribution "$WORK_DIR/content"; then
      is_candidate=true
      reasons+=("deleted file with attribution markers")
    fi

  elif [[ "$status_char" == "R" ]]; then
    # Renamed file: old_filepath has the original path
    if [[ $(wc -c < "$filepath" 2>/dev/null) -gt 102400 ]]; then
      echo "Warning: $filepath exceeds 100KB — only the first 100KB will be scanned for attribution markers." >&2
    fi
    head -c 102400 "$filepath" > "$WORK_DIR/content" 2>/dev/null || continue

    if has_vendor_path "$filepath" || has_vendor_path "${old_filepath:-}"; then
      is_candidate=true
      reasons+=("renamed file in vendor path")
    fi

    if has_third_party_attribution "$WORK_DIR/content"; then
      is_candidate=true
      reasons+=("renamed file with attribution markers")
    fi

  elif [[ "$status_char" == "M" ]]; then
    if [[ $(wc -c < "$filepath" 2>/dev/null) -gt 102400 ]]; then
      echo "Warning: $filepath exceeds 100KB — only the first 100KB will be scanned for attribution markers." >&2
    fi
    head -c 102400 "$filepath" > "$WORK_DIR/content" 2>/dev/null || continue
    diff_output=$(combined_diff "$filepath")

    has_added=false
    has_removed=false
    has_added_attribution_lines "$diff_output" && has_added=true
    has_removed_attribution_lines "$diff_output" && has_removed=true

    if [[ "$has_added" == "false" && "$has_removed" == "false" ]]; then
      continue
    fi

    if [[ "$has_added" == "true" && "$has_removed" == "true" ]]; then
      reasons+=("attribution markers modified")
    elif [[ "$has_added" == "true" ]]; then
      # The diff-hunk scan uses broad patterns (e.g., "copyright", "licensed") that
      # match first-party headers too. When markers were only added (not removed),
      # filter out files whose full content has no third-party attribution — those
      # are Sentry's own license headers being added.
      if ! has_third_party_attribution "$WORK_DIR/content" && ! has_vendor_path "$filepath"; then
        continue
      fi
      reasons+=("attribution markers added")
    else
      # Mirror the added-only guard: check the merge-base content for third-party
      # attribution so we don't flag removal of Sentry's own copyright headers.
      git show "$MERGE_BASE":"$filepath" > "$WORK_DIR/old_content" 2>/dev/null || continue
      if ! has_third_party_attribution "$WORK_DIR/old_content" && ! has_vendor_path "$filepath"; then
        continue
      fi
      reasons+=("attribution markers removed")
    fi
    is_candidate=true

    has_vendor_path "$filepath" && reasons+=("file in vendor path")
  fi

  if [[ "$is_candidate" == "false" ]]; then
    continue
  fi

  # Format reasons as comma-separated string
  reasons_str=$(printf '%s, ' "${reasons[@]+${reasons[@]}}" | sed 's/, $//')

  # Output structured block
  echo "---"
  echo "file: $filepath"
  echo "status: $status_char"
  echo "reasons: $reasons_str"
  echo "---"

  found_any=true

done < <(collect_changed_files)

if [[ "$found_any" == "true" ]] || [[ "$notices_file_changed" == "true" ]]; then
  exit 10
fi
