#!/usr/bin/env bash
# shellcheck shell=bash
#
# Tests for find-attribution-candidates.sh
#
# Each test creates a temporary git repo, sets up a specific scenario,
# runs the script, and asserts on its output and exit code.
#
# Usage: bash test/test-find-attribution-candidates.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SCRIPT="$SCRIPT_DIR/../find-attribution-candidates.sh"

TESTS_RUN=0
TESTS_PASSED=0
TESTS_FAILED=0

RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m'

# --- Helpers ---

setup_repo() {
  local tmpdir
  tmpdir=$(mktemp -d)
  git -C "$tmpdir" init -b main --quiet
  git -C "$tmpdir" config user.email "test@test.com"
  git -C "$tmpdir" config user.name "Test"

  cat > "$tmpdir/THIRD_PARTY_NOTICES.md" << 'NOTICES'
# Third-Party Notices

## Example Library (Apache 2.0)

- Source: https://github.com/example/library
- License: Apache License 2.0
- Copyright: Copyright 2024 Example Inc.
- Scope: `src/main/java/com/example/Foo.java`
NOTICES
  git -C "$tmpdir" add THIRD_PARTY_NOTICES.md
  git -C "$tmpdir" commit -m "Initial commit" --quiet

  echo "$tmpdir"
}

setup_branch() {
  git checkout -b test-branch --quiet
}

cleanup_repo() {
  rm -rf "$1"
}

run_script() {
  bash "$SCRIPT" 2>&1
}

get_field() {
  local output="$1" field="$2"
  echo "$output" | grep "^${field}: " | head -1 | sed "s/^${field}: //"
}

assert_eq() {
  local actual="$1" expected="$2" msg="$3"
  if [[ "$actual" == "$expected" ]]; then
    return 0
  fi
  echo -e "  ${RED}FAIL${NC}: $msg"
  echo "    expected: '$expected'"
  echo "    actual:   '$actual'"
  return 1
}

assert_contains() {
  local haystack="$1" needle="$2" msg="$3"
  if [[ "$haystack" == *"$needle"* ]]; then
    return 0
  fi
  echo -e "  ${RED}FAIL${NC}: $msg"
  echo "    expected to contain: '$needle'"
  echo "    actual: '$haystack'"
  return 1
}

# Script contract: stdout always begins with these two lines (in order).
assert_global_metadata_prefix() {
  local output="$1" exists="$2" changed="$3" msg="$4"
  assert_eq "$(echo "$output" | head -n 1)" "notices_file_exists: $exists" "${msg} — notices_file_exists line" || return 1
  assert_eq "$(echo "$output" | sed -n '2p')" "notices_file_changed: $changed" "${msg} — notices_file_changed line" || return 1
}

assert_line_count() {
  local output="$1" expected="$2" msg="$3"
  local count
  count=$(printf '%s\n' "$output" | wc -l | tr -d '[:space:]')
  assert_eq "$count" "$expected" "$msg" || return 1
}

run_test() {
  local test_name="$1" test_fn="$2"
  TESTS_RUN=$((TESTS_RUN + 1))

  local tmpdir original_dir
  tmpdir=$(setup_repo)
  original_dir=$(pwd)
  cd "$tmpdir"

  local failed=false
  echo -n "$test_name ... "

  if ! $test_fn; then
    failed=true
  fi

  cd "$original_dir"
  cleanup_repo "$tmpdir"

  if [[ "$failed" == "true" ]]; then
    TESTS_FAILED=$((TESTS_FAILED + 1))
    echo -e "${RED}FAILED${NC}"
  else
    TESTS_PASSED=$((TESTS_PASSED + 1))
    echo -e "${GREEN}ok${NC}"
  fi
}

# --- Test cases ---

test_clean_branch_no_candidates() {
  setup_branch
  mkdir -p src
  echo "package com.example; public class Clean {}" > src/Clean.java
  git add src/Clean.java
  git commit -m "Add clean file" --quiet

  local output exit_code=0
  output=$(run_script) || exit_code=$?
  assert_eq "$exit_code" "0" "should exit 0 when no candidates and NOTICES unchanged" || return 1
  assert_global_metadata_prefix "$output" true false "clean branch" || return 1
  assert_line_count "$output" 2 "exit 0 with no work should print exactly metadata lines" || return 1
}

test_new_file_with_attribution() {
  setup_branch
  mkdir -p src
  cat > src/Adapted.java << 'JAVA'
// Adapted from SomeLibrary.
// Copyright 2024 Some Author.
// Licensed under the Apache License 2.0.
// https://github.com/some/library
package com.example;
public class Adapted {}
JAVA
  git add src/Adapted.java
  git commit -m "Add adapted file" --quiet

  local output exit_code=0 ok=true
  output=$(run_script) || exit_code=$?
  assert_eq "$exit_code" "10" "should exit 10 when candidates found" || ok=false
  assert_global_metadata_prefix "$output" true false "candidate with NOTICES unchanged" || ok=false
  assert_eq "$(echo "$output" | sed -n '3p')" "---" "metadata lines precede first candidate block" || ok=false
  assert_eq "$(get_field "$output" "file")" "src/Adapted.java" "file path" || ok=false
  assert_eq "$(get_field "$output" "status")" "A" "status" || ok=false
  assert_contains "$(get_field "$output" "reasons")" "attribution markers in file" "reasons" || ok=false
  [[ "$ok" == "true" ]]
}

test_new_file_in_vendor_path() {
  setup_branch
  mkdir -p vendor/lib
  echo "public class Foo {}" > vendor/lib/Foo.java
  git add vendor/lib/Foo.java
  git commit -m "Add vendored file" --quiet

  local output exit_code=0 ok=true
  output=$(run_script) || exit_code=$?
  assert_eq "$exit_code" "10" "should exit 10 when vendor path candidate found" || ok=false
  assert_eq "$(get_field "$output" "file")" "vendor/lib/Foo.java" "file path" || ok=false
  assert_contains "$(get_field "$output" "reasons")" "path suggests vendored code" "reasons" || ok=false
  [[ "$ok" == "true" ]]
}

test_new_file_under_io_sentry_vendor_path() {
  setup_branch
  mkdir -p io/sentry/vendor
  echo "public class VendorStub {}" > io/sentry/vendor/VendorStub.java
  git add io/sentry/vendor/VendorStub.java
  git commit -m "Add file under io/sentry/vendor" --quiet

  local output exit_code=0 ok=true
  output=$(run_script) || exit_code=$?
  assert_eq "$exit_code" "10" "should exit 10 when io/sentry/vendor path candidate found" || ok=false
  assert_eq "$(get_field "$output" "file")" "io/sentry/vendor/VendorStub.java" "file path" || ok=false
  assert_contains "$(get_field "$output" "reasons")" "path suggests vendored code" "reasons" || ok=false
  [[ "$ok" == "true" ]]
}

test_deleted_file_with_attribution() {
  mkdir -p src
  cat > src/Licensed.java << 'JAVA'
// Adapted from OldLib.
// Copyright 2023 Old Author.
// Licensed under the MIT License.
package com.example;
public class Licensed {}
JAVA
  git add src/Licensed.java
  git commit -m "Add licensed file" --quiet

  setup_branch
  git rm src/Licensed.java --quiet
  git commit -m "Remove licensed file" --quiet

  local output exit_code=0 ok=true
  output=$(run_script) || exit_code=$?
  assert_eq "$exit_code" "10" "should exit 10 when candidates found" || ok=false
  assert_eq "$(get_field "$output" "file")" "src/Licensed.java" "file path" || ok=false
  assert_eq "$(get_field "$output" "status")" "D" "status" || ok=false
  assert_contains "$(get_field "$output" "reasons")" "deleted file with attribution markers" "reasons" || ok=false
  [[ "$ok" == "true" ]]
}

test_modified_file_attribution_added() {
  mkdir -p src
  echo "package com.example; public class Mod {}" > src/Mod.java
  git add src/Mod.java
  git commit -m "Add file" --quiet

  setup_branch
  cat > src/Mod.java << 'JAVA'
// Adapted from NewLib.
// Copyright 2024 New Author.
// Licensed under the Apache License 2.0.
package com.example;
public class Mod {}
JAVA
  git add src/Mod.java
  git commit -m "Add attribution" --quiet

  local output exit_code=0 ok=true
  output=$(run_script) || exit_code=$?
  assert_eq "$exit_code" "10" "should exit 10 when candidates found" || ok=false
  assert_eq "$(get_field "$output" "status")" "M" "status" || ok=false
  assert_contains "$(get_field "$output" "reasons")" "attribution markers added" "reasons" || ok=false
  [[ "$ok" == "true" ]]
}

test_modified_file_attribution_removed() {
  mkdir -p src
  cat > src/Strip.java << 'JAVA'
// Adapted from StripLib.
// Copyright 2024 Strip Author.
// Licensed under the MIT License.
package com.example;
public class Strip {}
JAVA
  git add src/Strip.java
  git commit -m "Add attributed file" --quiet

  setup_branch
  cat > src/Strip.java << 'JAVA'
package com.example;
public class Strip {}
JAVA
  git add src/Strip.java
  git commit -m "Remove attribution" --quiet

  local output exit_code=0 ok=true
  output=$(run_script) || exit_code=$?
  assert_eq "$exit_code" "10" "should exit 10 when candidates found" || ok=false
  assert_eq "$(get_field "$output" "status")" "M" "status" || ok=false
  assert_contains "$(get_field "$output" "reasons")" "attribution markers removed" "reasons" || ok=false
  [[ "$ok" == "true" ]]
}

test_renamed_file_has_correct_path() {
  mkdir -p src/old
  cat > src/old/Lib.java << 'JAVA'
// Adapted from RenameLib.
// Copyright 2024 Rename Author.
// Licensed under the Apache License 2.0.
package com.example;
public class Lib {}
JAVA
  git add src/old/Lib.java
  git commit -m "Add file" --quiet

  setup_branch
  mkdir -p src/new
  git mv src/old/Lib.java src/new/Lib.java
  git commit -m "Rename file" --quiet

  local output exit_code=0 ok=true
  output=$(run_script) || exit_code=$?
  assert_eq "$exit_code" "10" "should exit 10 when candidates found" || ok=false
  assert_eq "$(get_field "$output" "file")" "src/new/Lib.java" "file should be new path" || ok=false
  assert_eq "$(get_field "$output" "status")" "R" "status" || ok=false
  assert_contains "$(get_field "$output" "reasons")" "renamed file with attribution markers" "reasons" || ok=false
  [[ "$ok" == "true" ]]
}

test_staged_new_file_detected() {
  setup_branch
  echo "placeholder" > placeholder.txt
  git add placeholder.txt
  git commit -m "Placeholder" --quiet

  mkdir -p src
  cat > src/Staged.java << 'JAVA'
// Adapted from StagedLib.
// Copyright 2024 Staged Author.
// Licensed under the MIT License.
package com.example;
public class Staged {}
JAVA
  git add src/Staged.java
  # NOT committed

  local output exit_code=0 ok=true
  output=$(run_script) || exit_code=$?
  assert_eq "$exit_code" "10" "should exit 10 when staged candidate found" || ok=false
  assert_contains "$output" "src/Staged.java" "should detect staged file" || ok=false
  assert_contains "$(get_field "$output" "reasons")" "attribution markers in file" "reasons" || ok=false
  [[ "$ok" == "true" ]]
}

test_staged_modification_detected() {
  mkdir -p src
  echo "package com.example; public class StagedMod {}" > src/StagedMod.java
  git add src/StagedMod.java
  git commit -m "Add file" --quiet

  setup_branch
  echo "x" > placeholder.txt
  git add placeholder.txt
  git commit -m "Placeholder" --quiet

  cat > src/StagedMod.java << 'JAVA'
// Adapted from StagedModLib.
// Copyright 2024 StagedMod Author.
// Licensed under the Apache License 2.0.
package com.example;
public class StagedMod {}
JAVA
  git add src/StagedMod.java
  # NOT committed — staged only

  local output exit_code=0 ok=true
  output=$(run_script) || exit_code=$?
  assert_eq "$exit_code" "10" "should exit 10 when staged modification detected" || ok=false
  assert_contains "$output" "src/StagedMod.java" "should detect staged modification" || ok=false
  assert_contains "$(get_field "$output" "reasons")" "attribution markers added" "reasons" || ok=false
  [[ "$ok" == "true" ]]
}

test_untracked_file_detected() {
  setup_branch
  echo "placeholder" > placeholder.txt
  git add placeholder.txt
  git commit -m "Placeholder" --quiet

  mkdir -p src
  cat > src/Untracked.java << 'JAVA'
// Adapted from UntrackedLib.
// Copyright 2024 Untracked Author.
// Licensed under the Apache License 2.0.
package com.example;
public class Untracked {}
JAVA
  # NOT staged, NOT committed

  local output exit_code=0 ok=true
  output=$(run_script) || exit_code=$?
  assert_eq "$exit_code" "10" "should exit 10 when untracked candidate found" || ok=false
  assert_contains "$output" "src/Untracked.java" "should detect untracked file" || ok=false
  [[ "$ok" == "true" ]]
}

test_notices_file_changed_true() {
  setup_branch
  mkdir -p src
  cat > src/Noticed.java << 'JAVA'
// Adapted from NoticedLib.
// Copyright 2024 Noticed Author.
// Licensed under the Apache License 2.0.
// https://github.com/example/library
package com.example;
public class Noticed {}
JAVA
  echo "## New Entry" >> THIRD_PARTY_NOTICES.md
  git add src/Noticed.java THIRD_PARTY_NOTICES.md
  git commit -m "Add attributed file and update notices" --quiet

  local output exit_code=0 ok=true
  output=$(run_script) || exit_code=$?
  assert_eq "$exit_code" "10" "should exit 10 when candidates or NOTICES changed" || ok=false
  assert_global_metadata_prefix "$output" true true "committed NOTICES + candidate" || ok=false
  assert_eq "$(echo "$output" | sed -n '3p')" "---" "metadata precedes candidate block" || ok=false
  [[ "$ok" == "true" ]]
}

test_notices_only_change_triggers_without_file_candidates() {
  setup_branch
  printf '\n### Doc-only tweak\nPaths refreshed.\n' >> THIRD_PARTY_NOTICES.md
  git add THIRD_PARTY_NOTICES.md
  git commit -m "Doc-only NOTICES update" --quiet

  local output exit_code=0 ok=true
  output=$(run_script) || exit_code=$?
  assert_eq "$exit_code" "10" "THIRD_PARTY_NOTICES.md change must exit 10" || ok=false
  assert_global_metadata_prefix "$output" true true "NOTICES-only committed doc tweak" || ok=false
  local count
  count=$(echo "$output" | grep -c "^file: " || true)
  assert_eq "$count" "0" "no file candidate blocks when diff avoids attribution keywords" || ok=false
  assert_line_count "$output" 2 "NOTICES-only signal should be exactly two metadata lines" || ok=false
  [[ "$ok" == "true" ]]
}

test_notices_only_unstaged_triggers_without_file_candidates() {
  setup_branch
  echo "placeholder" > placeholder.txt
  git add placeholder.txt
  git commit -m "Placeholder" --quiet

  printf '\n### Unstaged doc tweak\nNo license keywords here.\n' >> THIRD_PARTY_NOTICES.md
  # NOT staged — worktree-only change to NOTICES

  local output exit_code=0 ok=true
  output=$(run_script) || exit_code=$?
  assert_eq "$exit_code" "10" "unstaged NOTICES change must exit 10" || ok=false
  assert_global_metadata_prefix "$output" true true "unstaged NOTICES only" || ok=false
  local count
  count=$(echo "$output" | grep -c "^file: " || true)
  assert_eq "$count" "0" "no file blocks for NOTICES-only unstaged edit" || ok=false
  assert_line_count "$output" 2 "stdout should be metadata only" || ok=false
  [[ "$ok" == "true" ]]
}

# NOTICES exists at merge-base (main) but is removed on the feature branch only.
test_notices_deleted_on_branch() {
  setup_branch
  git rm THIRD_PARTY_NOTICES.md --quiet
  git commit -m "Remove THIRD_PARTY_NOTICES on branch" --quiet

  local output exit_code=0 ok=true
  output=$(run_script) || exit_code=$?
  assert_eq "$exit_code" "10" "NOTICES deletion on branch must exit 10" || ok=false
  assert_global_metadata_prefix "$output" false true "NOTICES deleted vs merge-base" || ok=false
  local count
  count=$(echo "$output" | grep -c "^file: " || true)
  assert_eq "$count" "0" "no file candidates when only NOTICES is deleted" || ok=false
  assert_line_count "$output" 2 "metadata only when NOTICES-only deletion" || ok=false
  [[ "$ok" == "true" ]]
}

test_excluded_paths_skipped() {
  setup_branch
  mkdir -p .github/workflows
  cat > .github/workflows/Licensed.java << 'JAVA'
// Adapted from ExcludedLib.
// Copyright 2024 Excluded Author.
// Licensed under the MIT License.
package com.example;
public class Licensed {}
JAVA
  git add .github/workflows/Licensed.java
  git commit -m "Add file in excluded path" --quiet

  local output exit_code=0
  output=$(run_script) || exit_code=$?
  assert_eq "$exit_code" "0" "excluded paths should produce no candidates" || return 1
  assert_global_metadata_prefix "$output" true false "excluded path" || return 1
  assert_line_count "$output" 2 "exit 0 should print metadata only" || return 1
}

test_generated_files_skipped() {
  setup_branch
  mkdir -p src/generated/com/example
  cat > src/generated/com/example/R.java << 'JAVA'
// Adapted from GeneratedLib.
// Copyright 2024 Generated Author.
// Licensed under the Apache License 2.0.
package com.example;
public class R {}
JAVA
  git add src/generated/com/example/R.java
  git commit -m "Add generated file" --quiet

  local output exit_code=0
  output=$(run_script) || exit_code=$?
  assert_eq "$exit_code" "0" "generated files should produce no candidates" || return 1
  assert_global_metadata_prefix "$output" true false "generated file skipped" || return 1
  assert_line_count "$output" 2 "exit 0 should print metadata only" || return 1
}

test_sentry_copyright_not_flagged() {
  setup_branch
  mkdir -p src
  cat > src/SentryOwned.java << 'JAVA'
// Copyright 2024 Functional Software, Inc.
package com.example;
public class SentryOwned {}
JAVA
  git add src/SentryOwned.java
  git commit -m "Add Sentry-owned file" --quiet

  local output exit_code=0
  output=$(run_script) || exit_code=$?
  assert_eq "$exit_code" "0" "Sentry copyright should not trigger attribution" || return 1
  assert_global_metadata_prefix "$output" true false "Sentry-only copyright" || return 1
  assert_line_count "$output" 2 "exit 0 should print metadata only" || return 1
}


test_notices_staged_change_detected() {
  setup_branch
  mkdir -p src
  cat > src/StagedNotice.java << 'JAVA'
// Adapted from StagedNoticeLib.
// Copyright 2024 StagedNotice Author.
// Licensed under the Apache License 2.0.
package com.example;
public class StagedNotice {}
JAVA
  git add src/StagedNotice.java
  git commit -m "Add file" --quiet

  # Stage a change to THIRD_PARTY_NOTICES.md but don't commit
  echo "## Staged Entry" >> THIRD_PARTY_NOTICES.md
  git add THIRD_PARTY_NOTICES.md
  # NOT committed

  local output exit_code=0 ok=true
  output=$(run_script) || exit_code=$?
  assert_eq "$exit_code" "10" "staged NOTICES change must exit 10" || ok=false
  assert_global_metadata_prefix "$output" true true "staged NOTICES with committed candidate" || ok=false
  [[ "$ok" == "true" ]]
}

test_modified_vendor_path_file_attribution_changed() {
  mkdir -p vendor/lib
  cat > vendor/lib/Vendored.java << 'JAVA'
// Adapted from VendoredLib.
// Copyright 2024 Vendored Author.
// Licensed under the Apache License 2.0.
// https://github.com/example/library
package com.example;
public class Vendored {
  void oldMethod() {}
}
JAVA
  git add vendor/lib/Vendored.java
  git commit -m "Add vendored file" --quiet

  setup_branch
  cat > vendor/lib/Vendored.java << 'JAVA'
// Adapted from VendoredLib.
// Copyright 2025 Vendored Author.
// Licensed under the Apache License 2.0.
// https://github.com/example/library
package com.example;
public class Vendored {
  void newMethod() {}
}
JAVA
  git add vendor/lib/Vendored.java
  git commit -m "Update vendored file" --quiet

  local output exit_code=0 ok=true
  output=$(run_script) || exit_code=$?
  assert_eq "$exit_code" "10" "should exit 10 when vendor-path attribution change detected" || ok=false
  assert_eq "$(get_field "$output" "status")" "M" "status" || ok=false
  assert_contains "$(get_field "$output" "reasons")" "attribution markers modified" "reasons should include attribution change" || ok=false
  assert_contains "$(get_field "$output" "reasons")" "file in vendor path" "reasons should include vendor path" || ok=false
  [[ "$ok" == "true" ]]
}

test_binary_file_skipped() {
  setup_branch
  mkdir -p src
  # Create a file with a null byte so git treats it as binary
  printf '// Adapted from BinaryLib.\n// Copyright 2024 Binary Author.\n\x00binary content' > src/Binary.dat
  git add src/Binary.dat
  git commit -m "Add binary file" --quiet

  local output exit_code=0
  output=$(run_script) || exit_code=$?
  assert_eq "$exit_code" "0" "binary files should be skipped" || return 1
  assert_global_metadata_prefix "$output" true false "binary skipped" || return 1
  assert_line_count "$output" 2 "exit 0 should print metadata only" || return 1
}

# Many first-party Sentry files carry the project's own Apache 2.0 license header.
# A license header alone (without a vendoring marker like "Adapted from" or a
# non-Sentry copyright) does not indicate vendored code and should not be flagged.
test_license_only_header_not_flagged() {
  setup_branch
  mkdir -p src
  cat > src/LicenseOnly.java << 'JAVA'
/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.example;
public class LicenseOnly {}
JAVA
  git add src/LicenseOnly.java
  git commit -m "Add file with license-only header" --quiet

  local output exit_code=0
  output=$(run_script) || exit_code=$?
  assert_eq "$exit_code" "0" "license-only header without third-party copyright should not trigger attribution" || return 1
  assert_global_metadata_prefix "$output" true false "license-only header" || return 1
  assert_line_count "$output" 2 "exit 0 should print metadata only" || return 1
}

test_modified_vendored_file_no_attribution_change() {
  mkdir -p src
  cat > src/Vendored.java << 'JAVA'
// Adapted from VendoredLib.
// Copyright 2024 Vendored Author.
// Licensed under the Apache License 2.0.
// https://github.com/example/library
package com.example;
public class Vendored {
  void oldMethod() {}
}
JAVA
  git add src/Vendored.java
  git commit -m "Add vendored file" --quiet

  setup_branch
  cat > src/Vendored.java << 'JAVA'
// Adapted from VendoredLib.
// Copyright 2024 Vendored Author.
// Licensed under the Apache License 2.0.
// https://github.com/example/library
package com.example;
public class Vendored {
  void newMethod() {}
}
JAVA
  git add src/Vendored.java
  git commit -m "Fix bug in vendored file" --quiet

  local output exit_code=0
  output=$(run_script) || exit_code=$?
  assert_eq "$exit_code" "0" "should not flag vendored file when only non-attribution lines changed" || return 1
  assert_global_metadata_prefix "$output" true false "vendored file non-attribution edit" || return 1
  assert_line_count "$output" 2 "exit 0 should print metadata only" || return 1
}

# Adding Sentry's own license header to a first-party file should not trigger.
test_modified_first_party_license_header_not_flagged() {
  mkdir -p src
  echo "package com.example; public class FirstParty {}" > src/FirstParty.java
  git add src/FirstParty.java
  git commit -m "Add file" --quiet

  setup_branch
  cat > src/FirstParty.java << 'JAVA'
/*
 * Copyright 2025 Functional Software, Inc.
 * Licensed under the Apache License, Version 2.0.
 */
package com.example;
public class FirstParty {}
JAVA
  git add src/FirstParty.java
  git commit -m "Add Sentry license header" --quiet

  local output exit_code=0
  output=$(run_script) || exit_code=$?
  assert_eq "$exit_code" "0" "adding Sentry's own license header should not trigger attribution" || return 1
  assert_global_metadata_prefix "$output" true false "first-party license header" || return 1
  assert_line_count "$output" 2 "exit 0 should print metadata only" || return 1
}

test_unstaged_modification_detected() {
  mkdir -p src
  echo "package com.example; public class Unstaged {}" > src/Unstaged.java
  git add src/Unstaged.java
  git commit -m "Add file" --quiet

  setup_branch
  echo "x" > placeholder.txt
  git add placeholder.txt
  git commit -m "Placeholder" --quiet

  cat > src/Unstaged.java << 'JAVA'
// Adapted from UnstagedLib.
// Copyright 2024 Unstaged Author.
// Licensed under the Apache License 2.0.
package com.example;
public class Unstaged {}
JAVA
  # NOT staged, NOT committed — worktree only

  local output exit_code=0 ok=true
  output=$(run_script) || exit_code=$?
  assert_eq "$exit_code" "10" "should exit 10 when unstaged modification detected" || ok=false
  assert_contains "$output" "src/Unstaged.java" "should detect unstaged modification" || ok=false
  assert_contains "$(get_field "$output" "reasons")" "attribution markers added" "reasons" || ok=false
  [[ "$ok" == "true" ]]
}

test_multiple_candidates() {
  setup_branch
  mkdir -p src
  cat > src/First.java << 'JAVA'
// Adapted from FirstLib.
// Copyright 2024 First Author.
// Licensed under the MIT License.
package com.example;
public class First {}
JAVA
  cat > src/Second.java << 'JAVA'
// Adapted from SecondLib.
// Copyright 2024 Second Author.
// Licensed under the Apache License 2.0.
package com.example;
public class Second {}
JAVA
  git add src/First.java src/Second.java
  git commit -m "Add two attributed files" --quiet

  local output exit_code=0 ok=true
  output=$(run_script) || exit_code=$?
  assert_eq "$exit_code" "10" "should exit 10 when candidates found" || ok=false
  local count
  count=$(echo "$output" | grep -c "^file: " || true)
  assert_eq "$count" "2" "should find exactly 2 candidates" || ok=false
  assert_contains "$output" "src/First.java" "should include First.java" || ok=false
  assert_contains "$output" "src/Second.java" "should include Second.java" || ok=false
  [[ "$ok" == "true" ]]
}

test_missing_notices_file() {
  git rm THIRD_PARTY_NOTICES.md --quiet
  git commit -m "Remove notices file" --quiet

  setup_branch
  mkdir -p src
  cat > src/Vendored.java << 'JAVA'
// Adapted from SomeLib.
// Copyright 2024 Some Author.
// Licensed under the Apache License 2.0.
package com.example;
public class Vendored {}
JAVA
  git add src/Vendored.java
  git commit -m "Add vendored file" --quiet

  local output exit_code=0 ok=true
  output=$(run_script) || exit_code=$?
  assert_eq "$exit_code" "10" "should still find candidates" || ok=false
  assert_global_metadata_prefix "$output" false false "missing NOTICES with vendored candidate" || ok=false
  assert_eq "$(echo "$output" | sed -n '3p')" "---" "metadata precedes candidate block" || ok=false
  [[ "$ok" == "true" ]]
}

test_merge_base_failure() {
  git checkout --orphan no-main --quiet 2>/dev/null
  echo "orphan" > orphan.txt
  git add orphan.txt
  git commit -m "Orphan commit" --quiet
  git branch -D main --quiet 2>/dev/null || true

  local exit_code=0 output
  output=$(run_script) || exit_code=$?

  local ok=true
  assert_eq "$exit_code" "2" "should exit 2 when merge-base fails" || ok=false
  assert_contains "$output" "could not determine merge-base" "error message" || ok=false
  [[ "$ok" == "true" ]]
}

test_renamed_into_vendor_path() {
  mkdir -p src
  echo "public class Moved {}" > src/Moved.java
  git add src/Moved.java
  git commit -m "Add file" --quiet

  setup_branch
  mkdir -p vendor/lib
  git mv src/Moved.java vendor/lib/Moved.java
  git commit -m "Move to vendor path" --quiet

  local output exit_code=0 ok=true
  output=$(run_script) || exit_code=$?
  assert_eq "$exit_code" "10" "should exit 10 when renamed into vendor path" || ok=false
  assert_eq "$(get_field "$output" "file")" "vendor/lib/Moved.java" "file should be new vendor path" || ok=false
  assert_eq "$(get_field "$output" "status")" "R" "status" || ok=false
  assert_contains "$(get_field "$output" "reasons")" "renamed file in vendor path" "reasons" || ok=false
  [[ "$ok" == "true" ]]
}

test_renamed_out_of_vendor_path() {
  mkdir -p vendor/lib
  cat > vendor/lib/Leaving.java << 'JAVA'
// Adapted from LeavingLib.
// Copyright 2024 Leaving Author.
// Licensed under the MIT License.
package com.example;
public class Leaving {}
JAVA
  git add vendor/lib/Leaving.java
  git commit -m "Add vendored file" --quiet

  setup_branch
  mkdir -p src
  git mv vendor/lib/Leaving.java src/Leaving.java
  git commit -m "Move out of vendor path" --quiet

  local output exit_code=0 ok=true
  output=$(run_script) || exit_code=$?
  assert_eq "$exit_code" "10" "should exit 10 when renamed out of vendor path" || ok=false
  assert_eq "$(get_field "$output" "file")" "src/Leaving.java" "file should be new path" || ok=false
  assert_eq "$(get_field "$output" "status")" "R" "status" || ok=false
  assert_contains "$(get_field "$output" "reasons")" "renamed file in vendor path" "reasons" || ok=false
  [[ "$ok" == "true" ]]
}

test_renamed_with_attribution_stripped() {
  mkdir -p src/old
  # File must be large enough that removing the 3-line header stays above git's
  # 50% rename-detection similarity threshold.
  cat > src/old/Stripped.java << 'JAVA'
// Adapted from StrippedLib.
// Copyright 2024 Stripped Author.
// Licensed under the MIT License.
package com.example;
public class Stripped {
  private int field1;
  private int field2;
  private int field3;
  public void method1() { field1 = 1; }
  public void method2() { field2 = 2; }
  public void method3() { field3 = 3; }
  public int getField1() { return field1; }
  public int getField2() { return field2; }
  public int getField3() { return field3; }
}
JAVA
  git add src/old/Stripped.java
  git commit -m "Add attributed file" --quiet

  setup_branch
  mkdir -p src/new
  git mv src/old/Stripped.java src/new/Stripped.java
  cat > src/new/Stripped.java << 'JAVA'
package com.example;
public class Stripped {
  private int field1;
  private int field2;
  private int field3;
  public void method1() { field1 = 1; }
  public void method2() { field2 = 2; }
  public void method3() { field3 = 3; }
  public int getField1() { return field1; }
  public int getField2() { return field2; }
  public int getField3() { return field3; }
}
JAVA
  git add src/new/Stripped.java
  git commit -m "Rename and strip attribution" --quiet

  local output exit_code=0 ok=true
  output=$(run_script) || exit_code=$?
  assert_eq "$exit_code" "10" "should exit 10 when attribution stripped during rename" || ok=false
  assert_eq "$(get_field "$output" "file")" "src/new/Stripped.java" "file should be new path" || ok=false
  assert_eq "$(get_field "$output" "status")" "R" "status" || ok=false
  assert_contains "$(get_field "$output" "reasons")" "attribution markers stripped during rename" "reasons" || ok=false
  [[ "$ok" == "true" ]]
}

test_modified_sentry_copyright_year_bump_not_flagged() {
  mkdir -p src
  cat > src/SentryYear.java << 'JAVA'
// Copyright 2024 Functional Software, Inc.
// Licensed under the Apache License, Version 2.0.
package com.example;
public class SentryYear {}
JAVA
  git add src/SentryYear.java
  git commit -m "Add file with Sentry copyright" --quiet

  setup_branch
  cat > src/SentryYear.java << 'JAVA'
// Copyright 2025 Functional Software, Inc.
// Licensed under the Apache License, Version 2.0.
package com.example;
public class SentryYear {}
JAVA
  git add src/SentryYear.java
  git commit -m "Bump copyright year" --quiet

  local output exit_code=0
  output=$(run_script) || exit_code=$?
  assert_eq "$exit_code" "0" "Sentry copyright year bump should not trigger attribution" || return 1
  assert_global_metadata_prefix "$output" true false "Sentry copyright year bump" || return 1
  assert_line_count "$output" 2 "exit 0 should print metadata only" || return 1
}

test_dual_copyright_sentry_and_third_party() {
  setup_branch
  mkdir -p src
  cat > src/DualCopyright.java << 'JAVA'
// Copyright 2024 Functional Software, Inc. and Example Corp
// Licensed under the Apache License 2.0.
package com.example;
public class DualCopyright {}
JAVA
  git add src/DualCopyright.java
  git commit -m "Add dual-copyright file" --quiet

  local output exit_code=0 ok=true
  output=$(run_script) || exit_code=$?
  assert_eq "$exit_code" "10" "should exit 10 for dual-copyright file" || ok=false
  assert_eq "$(get_field "$output" "file")" "src/DualCopyright.java" "file path" || ok=false
  assert_contains "$(get_field "$output" "reasons")" "attribution markers in file" "reasons" || ok=false
  [[ "$ok" == "true" ]]
}

test_committed_modified_then_staged_delete() {
  mkdir -p src
  cat > src/Conflict.java << 'JAVA'
// Adapted from ConflictLib.
// Copyright 2024 Conflict Author.
// Licensed under the MIT License.
package com.example;
public class Conflict {}
JAVA
  git add src/Conflict.java
  git commit -m "Add attributed file" --quiet

  setup_branch
  cat > src/Conflict.java << 'JAVA'
// Adapted from ConflictLib.
// Copyright 2025 Conflict Author.
// Licensed under the MIT License.
package com.example;
public class Conflict { void updated() {} }
JAVA
  git add src/Conflict.java
  git commit -m "Modify file" --quiet

  git rm src/Conflict.java --quiet

  local output exit_code=0 ok=true
  output=$(run_script) || exit_code=$?
  assert_eq "$exit_code" "10" "should exit 10 when candidates found" || ok=false
  assert_eq "$(get_field "$output" "file")" "src/Conflict.java" "file path" || ok=false
  assert_eq "$(get_field "$output" "status")" "D" "staged delete should override committed modify" || ok=false
  assert_contains "$(get_field "$output" "reasons")" "deleted file with attribution markers" "reasons" || ok=false
  [[ "$ok" == "true" ]]
}

test_notices_file_not_emitted_as_candidate() {
  setup_branch
  mkdir -p src
  cat > src/Vendored.java << 'JAVA'
// Adapted from SomeLib.
// Copyright 2024 Some Author.
// Licensed under the Apache License 2.0.
package com.example;
public class Vendored {}
JAVA
  echo "## New Entry" >> THIRD_PARTY_NOTICES.md
  git add src/Vendored.java THIRD_PARTY_NOTICES.md
  git commit -m "Add vendored file and update notices" --quiet

  local output exit_code=0 ok=true
  output=$(run_script) || exit_code=$?
  assert_eq "$exit_code" "10" "should exit 10 when candidates found" || ok=false
  assert_global_metadata_prefix "$output" true true "NOTICES changed with candidate" || ok=false
  local count
  count=$(echo "$output" | grep -c "^file: " || true)
  assert_eq "$count" "1" "THIRD_PARTY_NOTICES.md must not appear as a candidate" || ok=false
  assert_eq "$(get_field "$output" "file")" "src/Vendored.java" "only the source file should be a candidate" || ok=false
  [[ "$ok" == "true" ]]
}

test_removed_sentry_copyright_not_flagged() {
  mkdir -p src
  cat > src/SentryFile.java << 'JAVA'
// Copyright 2025 Functional Software, Inc.
// Licensed under the Apache License, Version 2.0.
package com.example;
public class SentryFile {}
JAVA
  git add src/SentryFile.java
  git commit -m "Add file with Sentry copyright" --quiet

  setup_branch
  cat > src/SentryFile.java << 'JAVA'
package com.example;
public class SentryFile {}
JAVA
  git add src/SentryFile.java
  git commit -m "Remove Sentry copyright" --quiet

  local output exit_code=0
  output=$(run_script) || exit_code=$?
  assert_eq "$exit_code" "0" "removing Sentry-only copyright should not trigger attribution" || return 1
  assert_global_metadata_prefix "$output" true false "removed Sentry copyright" || return 1
  assert_line_count "$output" 2 "exit 0 should print metadata only" || return 1
}

# --- Run all tests ---

run_test "Clean branch — no candidates"              test_clean_branch_no_candidates
run_test "New file with attribution markers"         test_new_file_with_attribution
run_test "New file in vendor path"                   test_new_file_in_vendor_path
run_test "New file under io/sentry/vendor"           test_new_file_under_io_sentry_vendor_path
run_test "Deleted file with attribution"             test_deleted_file_with_attribution
run_test "Modified file — attribution added"         test_modified_file_attribution_added
run_test "Modified file — attribution removed"       test_modified_file_attribution_removed
run_test "Renamed file — correct path"               test_renamed_file_has_correct_path
run_test "Staged new file detected"                  test_staged_new_file_detected
run_test "Staged modification detected"              test_staged_modification_detected
run_test "Untracked file detected"                   test_untracked_file_detected
run_test "THIRD_PARTY_NOTICES.md change — committed" test_notices_file_changed_true
run_test "THIRD_PARTY_NOTICES.md only — exit 10, no file blocks" test_notices_only_change_triggers_without_file_candidates
run_test "THIRD_PARTY_NOTICES.md only — unstaged, no file blocks" test_notices_only_unstaged_triggers_without_file_candidates
run_test "THIRD_PARTY_NOTICES.md deleted on branch"  test_notices_deleted_on_branch
run_test "THIRD_PARTY_NOTICES.md change — staged"    test_notices_staged_change_detected
run_test "THIRD_PARTY_NOTICES file not emitted as candidate" test_notices_file_not_emitted_as_candidate
run_test "Missing THIRD_PARTY_NOTICES.md"            test_missing_notices_file
run_test "Excluded paths skipped"                    test_excluded_paths_skipped
run_test "Generated files skipped"                   test_generated_files_skipped
run_test "Binary file skipped"                       test_binary_file_skipped
run_test "Sentry copyright not flagged"              test_sentry_copyright_not_flagged
run_test "Removed Sentry copyright — not flagged"    test_removed_sentry_copyright_not_flagged
run_test "License-only header not flagged"           test_license_only_header_not_flagged
run_test "Modified vendor-path file — attribution changed" test_modified_vendor_path_file_attribution_changed
run_test "Modified vendored file — no attribution change" test_modified_vendored_file_no_attribution_change
run_test "Modified first-party file — Sentry license header not flagged" test_modified_first_party_license_header_not_flagged
run_test "Unstaged modification detected"            test_unstaged_modification_detected
run_test "Multiple candidates in single run"         test_multiple_candidates
run_test "Merge-base failure"                        test_merge_base_failure
run_test "Renamed into vendor path"                  test_renamed_into_vendor_path
run_test "Renamed out of vendor path"                test_renamed_out_of_vendor_path
run_test "Renamed with attribution stripped"         test_renamed_with_attribution_stripped
run_test "Modified Sentry copyright year — not flagged" test_modified_sentry_copyright_year_bump_not_flagged
run_test "Dual copyright — Sentry and third-party"   test_dual_copyright_sentry_and_third_party
run_test "Committed M + staged D resolves to D"      test_committed_modified_then_staged_delete

# --- Summary ---

echo ""
echo "=============================="
echo "Tests run:    $TESTS_RUN"
echo -e "Passed:       ${GREEN}$TESTS_PASSED${NC}"
if [[ $TESTS_FAILED -gt 0 ]]; then
  echo -e "Failed:       ${RED}$TESTS_FAILED${NC}"
  exit 1
else
  echo "Failed:       0"
  echo -e "${GREEN}All tests passed.${NC}"
fi
