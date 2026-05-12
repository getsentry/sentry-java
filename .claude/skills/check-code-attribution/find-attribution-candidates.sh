#!/usr/bin/env bash
# shellcheck shell=bash
#
# Script for finding files in the current branch diff that may have added, modified, or removed an
# attribution for vendored code (those files are referred to as "attribution candidates"). When used
# with the check-code-attribution skill, this script lets us avoid sending work to the LLM that we
# can do ourselves.
#
# There are two types of candidates:
#
# 1. Source-file candidates (changed files with attribution markers):
#
#   ---
#   candidate_type: source_file
#   file: <path_to_candidate_file>
#   status: A|M|D|R (A = "added", M = "modified", D = "deleted", R = "renamed")
#   reasons: <comma-separated list of why this file was flagged as a candidate>
#   notices_match: url|none|deleted
#   notices_entry: <heading from THIRD_PARTY_NOTICES.md, if matched>
#   new_license_type: true|false
#   notices_file_exists: true|false
#   notices_file_changed: true|false
#   ---
#
# 2. NOTICES-entry candidates (entries removed or modified in THIRD_PARTY_NOTICES.md):
#
#   ---
#   candidate_type: notices_entry
#   notices_entry: <heading from THIRD_PARTY_NOTICES.md>
#   notices_change: removed|modified
#   scope_text_start:
#   <Scope section text from the old entry>
#   scope_text_end:
#   notices_file_exists: true|false
#   notices_file_changed: true|false
#   ---
#
# Exit code 0 = no candidates found, exit code 10 = candidates found.

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

# Combined pattern for diff-hunk scanning of modified files (includes generic terms
# "copyright" and "licensed" which are appropriate for individual changed lines)
ATTRIBUTION_PATTERN="$VENDORING_MARKERS|copyright|licensed|$LICENSE_NAMES"

# Sentry entity names — copyright lines mentioning these are treated as first-party
SENTRY_ENTITIES='functional software|getsentry|sentry software'

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

# Load exclusion patterns once into a temp file (regexes from a repo-controlled file — review
# changes carefully). Using a file avoids re-creating a process substitution per candidate.
EXCLUSION_PATTERNS_FILE=$(mktemp)
CONTENT_FILE=$(mktemp)
OLD_NOTICES=""
trap 'rm -f "$EXCLUSION_PATTERNS_FILE" "$CONTENT_FILE" ${OLD_NOTICES:+"$OLD_NOTICES"}' EXIT
if [[ -f "$EXCLUSIONS_FILE" ]]; then
  grep -v '^#' "$EXCLUSIONS_FILE" | grep -v '^$' > "$EXCLUSION_PATTERNS_FILE" || true
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

  # A non-Sentry copyright line is a strong standalone indicator.
  # Note: a line like "Copyright Sentry and Example Corp" is excluded because the Sentry entity
  # matches — split dual-copyright lines onto separate lines in the source to avoid this.
  grep -iE 'copyright' "$filepath" | grep -qivE "$SENTRY_ENTITIES" && return 0

  # License keywords alone (without a non-Sentry copyright or vendoring marker) do NOT
  # indicate vendored code — many first-party files carry the project's own license header.

  return 1
}

# Check if diff hunks contain added attribution-related lines
has_added_attribution_lines() {
  local diff_output="$1"
  grep -E '^\+' <<< "$diff_output" | grep -vE '^\+\+\+' | grep -qiE "$ATTRIBUTION_PATTERN"
}

# Check if diff hunks contain removed attribution-related lines
has_removed_attribution_lines() {
  local diff_output="$1"
  grep -E '^-' <<< "$diff_output" | grep -v '^---' | grep -qiE "$ATTRIBUTION_PATTERN"
}

# Extract URLs from a file. Capped at 50 to bound runtime on large files while still
# catching source URLs that appear after import blocks or lengthy preambles.
extract_urls() {
  { grep -oE 'https?://[^ )"<>]+' "$1" || true; } | head -50
}

# Try to match a candidate file to a THIRD_PARTY_NOTICES.md entry by URL.
# Collects all URLs, normalizes them, builds progressively shorter prefixes,
# and runs a single awk pass against THIRD_PARTY_NOTICES.md.
# Prints: "url<tab><heading>" if matched, "none" otherwise.
match_by_url() {
  local filepath="$1"
  local urls
  urls=$(extract_urls "$filepath")

  [[ -z "$urls" ]] && { echo "none"; return; }

  local normalized_urls
  normalized_urls=$(while IFS= read -r url; do
    echo "$url" | sed -E 's|https?://||' | sed 's|^www\.||' | sed 's|[[:space:]]*$||' | sed 's|/$||'
  done <<< "$urls")

  [[ -z "$normalized_urls" ]] && { echo "none"; return; }

  local result
  result=$(awk '
    function count_slashes(s,   i, c) {
      c = 0
      for (i = 1; i <= length(s); i++)
        if (substr(s, i, 1) == "/") c++
      return c
    }
    NR == FNR {
      u = tolower($0)
      while (index(u, "/") > 0) {
        # Require at least 2 slashes (e.g., github.com/org/repo) to avoid
        # matching at the domain or org level.
        if (count_slashes(u) >= 2)
          prefixes[n++] = u
        sub(/\/[^\/]*$/, "", u)
      }
      next
    }
    /^## / { current_heading = $0 }
    {
      line = tolower($0)
      for (i = 0; i < n; i++) {
        if (prefixes[i] != "" && index(line, prefixes[i]) > 0 && current_heading != "") {
          plen = length(prefixes[i])
          if (plen > best_len) {
            best_len = plen
            best_heading = current_heading
          }
        }
      }
    }
    END {
      if (best_len > 0)
        print "url\t" best_heading
    }
  ' <(echo "$normalized_urls") "$NOTICES_FILE" 2>/dev/null)

  if [[ -n "$result" ]]; then
    echo "$result"
  else
    echo "none"
  fi
}

# Extract known license types from THIRD_PARTY_NOTICES.md headings
extract_known_license_types() {
  grep '^## ' "$NOTICES_FILE" 2>/dev/null | grep -oE '\([^)]+\)' | tr -d '()' | sort -u
}

# Detect the license type declared in a file's content
detect_license_type() {
  local filepath="$1"

  # SPDX identifiers are the most precise — check them first
  local spdx_id
  spdx_id=$(grep -oiE 'SPDX-License-Identifier:[[:space:]]*[^ ]+' "$filepath" | head -1 | sed 's/.*:[[:space:]]*//')
  if [[ -n "$spdx_id" ]]; then
    local spdx_lower
    spdx_lower=$(echo "$spdx_id" | tr '[:upper:]' '[:lower:]')
    case "$spdx_lower" in
      apache-2.0)                echo "Apache 2.0"; return ;;
      mit)                       echo "MIT"; return ;;
      bsd-2-clause|bsd-3-clause) echo "BSD"; return ;;
      lgpl-*)                    echo "LGPL"; return ;;
      gpl-*)                     echo "GPL"; return ;;
      mpl-*)                     echo "MPL"; return ;;
      epl-*)                     echo "EPL"; return ;;
      isc)                       echo "ISC"; return ;;
      unlicense|cc0-*)           echo "Public Domain"; return ;;
      cc-by-*)                   echo "CC-BY"; return ;;
    esac
  fi

  if grep -qiE 'Apache License|Apache 2\.0' "$filepath"; then
    echo "Apache 2.0"
  elif grep -qiE 'MIT License' "$filepath"; then
    echo "MIT"
  elif grep -qiE 'BSD License|BSD [0-9]' "$filepath"; then
    echo "BSD"
  elif grep -qiE 'LGPL' "$filepath"; then
    echo "LGPL"
  elif grep -qiE 'GPL|GNU General Public' "$filepath"; then
    echo "GPL"
  elif grep -qiE 'Mozilla Public' "$filepath"; then
    echo "MPL"
  elif grep -qiE 'Eclipse Public License|EPL' "$filepath"; then
    echo "EPL"
  elif grep -qiE 'ISC License' "$filepath"; then
    echo "ISC"
  elif grep -qiE 'Unlicense|Public Domain' "$filepath"; then
    echo "Public Domain"
  elif grep -qiE 'Creative Commons|CC-BY' "$filepath"; then
    echo "CC-BY"
  else
    echo "unknown"
  fi
}

# Extract the full entry content for a given ## heading from a NOTICES file.
# Returns all lines between the heading and the next ## heading (or EOF).
# Note: bare "---" lines are treated as section boundaries per THIRD_PARTY_NOTICES.md
# conventions. Fenced code blocks are tracked so "---" inside ``` blocks is ignored.
extract_entry() {
  local notices_path="$1" heading="$2"
  awk -v target="$heading" '
    /^## / {
      if (in_target) exit
      in_target = ($0 == target)
      next
    }
    in_target && /^```/ { in_code = !in_code }
    in_target && !in_code && /^---$/ { exit }
    in_target { print }
  ' "$notices_path"
}

# Extract the ### Scope section text for a given ## heading from a NOTICES file.
# Returns the descriptive paragraph(s) between "### Scope" and the next section
# boundary (---, ##, or EOF), excluding fenced code blocks.
extract_scope() {
  local notices_path="$1" heading="$2"
  awk -v target="$heading" '
    /^## / { in_target = ($0 == target); in_scope = 0; next }
    in_target && /^### Scope/ { in_scope = 1; next }
    in_target && in_scope && !in_code && /^(## |---)/ { exit }
    in_target && in_scope && /^```/ { in_code = !in_code; next }
    in_target && in_scope && !in_code { gsub(/^[[:space:]]+|[[:space:]]+$/, ""); if ($0 != "") print }
  ' "$notices_path"
}

# Collect changed files from all sources (committed, staged, unstaged, untracked),
# deduplicated by current filepath (first occurrence wins). The committed diff is listed
# first so its status character takes precedence — a file committed as "A" (added) won't
# be overridden by a staged or unstaged "M" (modified) for the same path.
collect_changed_files() {
  {
    git diff "$MERGE_BASE"..HEAD --name-status --find-renames 2>/dev/null || true
    git diff --cached --name-status --find-renames 2>/dev/null || true
    git diff --name-status --find-renames 2>/dev/null || true
    git ls-files --others --exclude-standard 2>/dev/null | while IFS= read -r path; do
      [[ -n "$path" ]] && printf 'A\t%s\n' "$path"
    done
  } | awk -F'\t' '{
    # For renames (R###), the current path is the third field (new name).
    # For everything else, the current path is the last field.
    if ($1 ~ /^R/ && NF >= 3) key = $3
    else key = $NF
    if (!seen[key]++) print
  }'
}

# Diff from merge-base to the current worktree state (committed + staged + unstaged)
# in a single pass. No duplicate hunks because git diffs the merge-base blob
# directly against the current worktree file.
combined_diff() {
  local filepath="$1"
  git diff "$MERGE_BASE" -- "$filepath" 2>/dev/null || true
}

# Check if THIRD_PARTY_NOTICES.md was modified in this branch (committed, staged, or unstaged)
notices_file_changed="false"
if git diff "$MERGE_BASE"..HEAD --name-only -- "$NOTICES_FILE" 2>/dev/null | grep -q . \
  || git diff --cached --name-only -- "$NOTICES_FILE" 2>/dev/null | grep -q . \
  || git diff --name-only -- "$NOTICES_FILE" 2>/dev/null | grep -q .; then
  notices_file_changed="true"
fi

# Load known license types once (skip if NOTICES file doesn't exist)
KNOWN_LICENSES=""
if [[ "$notices_file_exists" == "true" ]]; then
  KNOWN_LICENSES=$(extract_known_license_types)
fi

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
    head -c 102400 "$filepath" > "$CONTENT_FILE" 2>/dev/null || continue

    if has_vendor_path "$filepath"; then
      is_candidate=true
      reasons+=("path suggests vendored code")
    fi

    if has_third_party_attribution "$CONTENT_FILE"; then
      is_candidate=true
      reasons+=("attribution markers in file")
    fi

  elif [[ "$status_char" == "D" ]]; then
    git show "$MERGE_BASE":"$filepath" > "$CONTENT_FILE" 2>/dev/null || continue

    if has_vendor_path "$filepath"; then
      is_candidate=true
      reasons+=("deleted file in vendor path")
    fi

    if has_third_party_attribution "$CONTENT_FILE"; then
      is_candidate=true
      reasons+=("deleted file with attribution markers")
    fi

  elif [[ "$status_char" == "R" ]]; then
    # Renamed file: old_filepath has the original path
    if [[ $(wc -c < "$filepath" 2>/dev/null) -gt 102400 ]]; then
      echo "Warning: $filepath exceeds 100KB — only the first 100KB will be scanned for attribution markers." >&2
    fi
    head -c 102400 "$filepath" > "$CONTENT_FILE" 2>/dev/null || continue

    if has_vendor_path "$filepath" || has_vendor_path "${old_filepath:-}"; then
      is_candidate=true
      reasons+=("renamed file in vendor path")
    fi

    if has_third_party_attribution "$CONTENT_FILE"; then
      is_candidate=true
      reasons+=("renamed file with attribution markers")
    fi

  elif [[ "$status_char" == "M" ]]; then
    if [[ $(wc -c < "$filepath" 2>/dev/null) -gt 102400 ]]; then
      echo "Warning: $filepath exceeds 100KB — only the first 100KB will be scanned for attribution markers." >&2
    fi
    head -c 102400 "$filepath" > "$CONTENT_FILE" 2>/dev/null || continue
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
      if ! has_third_party_attribution "$CONTENT_FILE" && ! has_vendor_path "$filepath"; then
        continue
      fi
      reasons+=("attribution markers added")
    else
      reasons+=("attribution markers removed")
    fi
    is_candidate=true

    has_vendor_path "$filepath" && reasons+=("file in vendor path")
  fi

  if [[ "$is_candidate" == "false" ]]; then
    continue
  fi

  # --- This file is an actionable candidate. Run deterministic checks. ---

  # THIRD_PARTY_NOTICES.md URL matching (skip for deleted files)
  notices_match="none"
  notices_entry=""
  new_license_type="false"

  if [[ "$status_char" == "D" ]]; then
    notices_match="deleted"
  else
    if [[ "$notices_file_exists" == "true" ]]; then
      match_result=$(match_by_url "$CONTENT_FILE")
      if [[ "$match_result" != "none" ]]; then
        notices_match=$(echo "$match_result" | cut -f1)
        notices_entry=$(echo "$match_result" | cut -f2-)
      fi

      # New license type check — uses exact whole-line matching (-xF) to avoid
      # false negatives like "GPL" matching "LGPL". Tradeoff: detect_license_type
      # may emit a short label (e.g. "EPL") while KNOWN_LICENSES has a versioned
      # form (e.g. "EPL 2.0"), producing a false positive (new_license_type: true
      # when the family is already represented). This is the safe direction — the
      # LLM just emits an extra "verify compatibility" reminder.
      candidate_license=$(detect_license_type "$CONTENT_FILE")
      if [[ "$candidate_license" != "unknown" ]]; then
        if ! echo "$KNOWN_LICENSES" | grep -qixF "$candidate_license"; then
          new_license_type="true"
        fi
      fi
    fi
  fi

  # Format reasons as comma-separated string
  reasons_str=$(printf '%s, ' "${reasons[@]+${reasons[@]}}" | sed 's/, $//')

  # Output structured block
  echo "---"
  echo "candidate_type: source_file"
  echo "file: $filepath"
  echo "status: $status_char"
  echo "reasons: $reasons_str"
  echo "notices_match: $notices_match"
  echo "notices_entry: $notices_entry"
  echo "new_license_type: $new_license_type"
  echo "notices_file_exists: $notices_file_exists"
  echo "notices_file_changed: $notices_file_changed"
  echo "---"

  found_any=true

done < <(collect_changed_files)

# --- NOTICES-entry analysis (detect removed or modified entries) ---
# When THIRD_PARTY_NOTICES.md was changed, diff the old and new entry headings to find
# entries that were removed or whose content was modified. This catches the reverse
# direction: source files are unchanged but their NOTICES entry was altered.
if [[ "$notices_file_changed" == "true" ]]; then
  OLD_NOTICES=$(mktemp)
  git show "$MERGE_BASE":"$NOTICES_FILE" > "$OLD_NOTICES" 2>/dev/null || true

  if [[ -s "$OLD_NOTICES" ]]; then
    old_headings=$({ grep '^## ' "$OLD_NOTICES" || true; } | sort)
    new_headings=""
    if [[ -f "$NOTICES_FILE" ]]; then
      new_headings=$({ grep '^## ' "$NOTICES_FILE" || true; } | sort)
    fi

    # Removed headings: in old but not in new
    while IFS= read -r heading; do
      [[ -z "$heading" ]] && continue
      scope_text=$(extract_scope "$OLD_NOTICES" "$heading")
      echo "---"
      echo "candidate_type: notices_entry"
      echo "notices_entry: $heading"
      echo "notices_change: removed"
      echo "scope_text_start:"
      [[ -n "$scope_text" ]] && echo "$scope_text"
      echo "scope_text_end:"
      echo "notices_file_exists: $notices_file_exists"
      echo "notices_file_changed: $notices_file_changed"
      echo "---"
      found_any=true
    done < <(comm -23 <(echo "$old_headings") <(echo "$new_headings"))

    # Modified entries: heading present in both, but entry content changed
    while IFS= read -r heading; do
      [[ -z "$heading" ]] && continue
      old_entry=$(extract_entry "$OLD_NOTICES" "$heading")
      new_entry=$(extract_entry "$NOTICES_FILE" "$heading")
      if [[ "$old_entry" != "$new_entry" ]]; then
        scope_text=$(extract_scope "$OLD_NOTICES" "$heading")
        echo "---"
        echo "candidate_type: notices_entry"
        echo "notices_entry: $heading"
        echo "notices_change: modified"
        echo "scope_text_start:"
        [[ -n "$scope_text" ]] && echo "$scope_text"
        echo "scope_text_end:"
        echo "notices_file_exists: $notices_file_exists"
        echo "notices_file_changed: $notices_file_changed"
        echo "---"
        found_any=true
      fi
    done < <(comm -12 <(echo "$old_headings") <(echo "$new_headings"))
  fi
fi

if [[ "$found_any" == "true" ]]; then
  exit 10
fi
