#!/usr/bin/env bash
# shellcheck shell=bash
#
# Tests for find-attribution-candidates.sh
#
# Each test creates a temporary git repo, sets up a specific scenario,
# runs the script, and asserts on its output and exit code.
#
# Usage: bash test-find-attribution-candidates.sh

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

get_scope_text() {
  local output="$1"
  echo "$output" | sed -n '/^scope_text_start:$/,/^scope_text_end:$/{ /^scope_text_start:$/d; /^scope_text_end:$/d; p; }'
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

  local exit_code=0
  run_script > /dev/null 2>&1 || exit_code=$?
  assert_eq "$exit_code" "0" "should exit 0 when no candidates"
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

  local output ok=true
  output=$(run_script) || true
  assert_eq "$(get_field "$output" "file")" "vendor/lib/Foo.java" "file path" || ok=false
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
  assert_eq "$(get_field "$output" "notices_match")" "deleted" "notices_match" || ok=false
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

  local output ok=true
  output=$(run_script) || true
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

  local output ok=true
  output=$(run_script) || true
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

  local output ok=true
  output=$(run_script) || true
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

  local output ok=true
  output=$(run_script) || true
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

  local output ok=true
  output=$(run_script) || true
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

  local output
  output=$(run_script) || true
  assert_eq "$(get_field "$output" "notices_file_changed")" "true" "notices_file_changed"
}

test_notices_url_matching() {
  setup_branch
  mkdir -p src
  cat > src/Matched.java << 'JAVA'
// Adapted from Example Library.
// Copyright 2024 Example Inc.
// Licensed under the Apache License 2.0.
// https://github.com/example/library
package com.example;
public class Matched {}
JAVA
  git add src/Matched.java
  git commit -m "Add file matching notices URL" --quiet

  local output ok=true
  output=$(run_script) || true
  assert_eq "$(get_field "$output" "notices_match")" "url" "notices_match" || ok=false
  assert_contains "$(get_field "$output" "notices_entry")" "Example Library" "notices_entry" || ok=false
  [[ "$ok" == "true" ]]
}

test_mit_no_false_positive_from_commit() {
  setup_branch
  mkdir -p src
  cat > src/CommitHelper.java << 'JAVA'
// Adapted from CommitLib.
// Copyright 2024 Commit Author.
package com.example;
public class CommitHelper {
  // This COMMIT message should not trigger MIT detection
  void commit() {}
}
JAVA
  git add src/CommitHelper.java
  git commit -m "Add file with COMMIT word" --quiet

  local output
  output=$(run_script) || true
  assert_eq "$(get_field "$output" "new_license_type")" "false" "COMMIT should not trigger MIT detection"
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

  local exit_code=0
  run_script > /dev/null 2>&1 || exit_code=$?
  assert_eq "$exit_code" "0" "excluded paths should produce no candidates"
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

  local exit_code=0
  run_script > /dev/null 2>&1 || exit_code=$?
  assert_eq "$exit_code" "0" "generated files should produce no candidates"
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

  local exit_code=0
  run_script > /dev/null 2>&1 || exit_code=$?
  assert_eq "$exit_code" "0" "Sentry copyright should not trigger attribution"
}

test_new_license_type_detected() {
  setup_branch
  mkdir -p src
  cat > src/MplFile.java << 'JAVA'
// Adapted from MplLib.
// Copyright 2024 Mpl Author.
// Licensed under the Mozilla Public License.
package com.example;
public class MplFile {}
JAVA
  git add src/MplFile.java
  git commit -m "Add MPL file" --quiet

  local output
  output=$(run_script) || true
  assert_eq "$(get_field "$output" "new_license_type")" "true" "MPL should be flagged as new license type"
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

  local output
  output=$(run_script) || true
  assert_eq "$(get_field "$output" "notices_file_changed")" "true" "staged notices change should be detected"
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

  local output ok=true
  output=$(run_script) || true
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

  local exit_code=0
  run_script > /dev/null 2>&1 || exit_code=$?
  assert_eq "$exit_code" "0" "binary files should be skipped"
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

  local exit_code=0
  run_script > /dev/null 2>&1 || exit_code=$?
  assert_eq "$exit_code" "0" "license-only header without third-party copyright should not trigger attribution"
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

  local exit_code=0
  run_script > /dev/null 2>&1 || exit_code=$?
  assert_eq "$exit_code" "0" "should not flag vendored file when only non-attribution lines changed"
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

  local exit_code=0
  run_script > /dev/null 2>&1 || exit_code=$?
  assert_eq "$exit_code" "0" "adding Sentry's own license header should not trigger attribution"
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

  local output ok=true
  output=$(run_script) || true
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
  assert_eq "$(get_field "$output" "notices_file_exists")" "false" "notices_file_exists" || ok=false
  assert_eq "$(get_field "$output" "notices_match")" "none" "notices_match" || ok=false
  assert_eq "$(get_field "$output" "new_license_type")" "false" "new_license_type should be false when no notices file" || ok=false
  [[ "$ok" == "true" ]]
}

test_progressive_url_matching() {
  setup_branch
  mkdir -p src
  cat > src/DeepUrl.java << 'JAVA'
// Adapted from Example Library.
// Copyright 2024 Example Inc.
// Licensed under the Apache License 2.0.
// https://github.com/example/library/tree/main/src/Foo.java
package com.example;
public class DeepUrl {}
JAVA
  git add src/DeepUrl.java
  git commit -m "Add file with deep URL" --quiet

  local output ok=true
  output=$(run_script) || true
  assert_eq "$(get_field "$output" "notices_match")" "url" "deep URL should match via progressive trimming" || ok=false
  assert_contains "$(get_field "$output" "notices_entry")" "Example Library" "notices_entry" || ok=false
  [[ "$ok" == "true" ]]
}

test_notices_entry_removed() {
  cat > THIRD_PARTY_NOTICES.md << 'NOTICES'
# Third-Party Notices

## Example Library (Apache 2.0)

**Source:** https://github.com/example/library
**License:** Apache License 2.0
**Copyright:** Copyright 2024 Example Inc.

### Scope

The code resides in `com.example.Foo`.

---

## Other Library (MIT)

**Source:** https://github.com/other/library
**License:** MIT License
**Copyright:** Copyright 2024 Other Inc.

### Scope

The code resides in `com.other.Bar`.
NOTICES
  git add THIRD_PARTY_NOTICES.md
  git commit -m "Two NOTICES entries" --quiet --amend

  setup_branch

  cat > THIRD_PARTY_NOTICES.md << 'NOTICES'
# Third-Party Notices

## Example Library (Apache 2.0)

**Source:** https://github.com/example/library
**License:** Apache License 2.0
**Copyright:** Copyright 2024 Example Inc.

### Scope

The code resides in `com.example.Foo`.
NOTICES
  git add THIRD_PARTY_NOTICES.md
  git commit -m "Remove Other Library entry" --quiet

  local output exit_code=0 ok=true
  output=$(run_script) || exit_code=$?
  assert_eq "$exit_code" "10" "should exit 10 when NOTICES entry removed" || ok=false
  assert_contains "$output" "candidate_type: notices_entry" "should have notices_entry candidate" || ok=false
  assert_contains "$output" "notices_change: removed" "should flag as removed" || ok=false
  assert_contains "$output" "## Other Library (MIT)" "should identify removed entry" || ok=false
  local scope
  scope=$(get_scope_text "$output")
  assert_contains "$scope" "com.other.Bar" "scope should reference source files" || ok=false
  [[ "$ok" == "true" ]]
}

test_notices_entry_modified() {
  cat > THIRD_PARTY_NOTICES.md << 'NOTICES'
# Third-Party Notices

## Example Library (Apache 2.0)

**Source:** https://github.com/example/library
**License:** Apache License 2.0
**Copyright:** Copyright 2024 Example Inc.

### Scope

The code resides in `com.example.Foo`.
NOTICES
  git add THIRD_PARTY_NOTICES.md
  git commit -m "NOTICES with entry" --quiet --amend

  setup_branch

  cat > THIRD_PARTY_NOTICES.md << 'NOTICES'
# Third-Party Notices

## Example Library (Apache 2.0)

**Source:** https://github.com/example/library
**License:** Apache License 2.0
**Copyright:** Copyright 2025 Example Inc.

### Scope

The code resides in `com.example.Foo`.
NOTICES
  git add THIRD_PARTY_NOTICES.md
  git commit -m "Change copyright year in NOTICES" --quiet

  local output exit_code=0 ok=true
  output=$(run_script) || exit_code=$?
  assert_eq "$exit_code" "10" "should exit 10 when NOTICES entry modified" || ok=false
  assert_contains "$output" "candidate_type: notices_entry" "should have notices_entry candidate" || ok=false
  assert_contains "$output" "notices_change: modified" "should flag as modified" || ok=false
  assert_contains "$output" "## Example Library (Apache 2.0)" "should identify modified entry" || ok=false
  [[ "$ok" == "true" ]]
}

test_source_file_has_candidate_type() {
  setup_branch
  mkdir -p src
  cat > src/Typed.java << 'JAVA'
// Adapted from TypedLib.
// Copyright 2024 Typed Author.
// Licensed under the Apache License 2.0.
package com.example;
public class Typed {}
JAVA
  git add src/Typed.java
  git commit -m "Add typed file" --quiet

  local output ok=true
  output=$(run_script) || true
  assert_contains "$output" "candidate_type: source_file" "should include candidate_type field" || ok=false
  [[ "$ok" == "true" ]]
}

# --- Run all tests ---

run_test "Clean branch — no candidates"              test_clean_branch_no_candidates
run_test "New file with attribution markers"         test_new_file_with_attribution
run_test "New file in vendor path"                   test_new_file_in_vendor_path
run_test "Deleted file with attribution"             test_deleted_file_with_attribution
run_test "Modified file — attribution added"         test_modified_file_attribution_added
run_test "Modified file — attribution removed"       test_modified_file_attribution_removed
run_test "Renamed file — correct path"               test_renamed_file_has_correct_path
run_test "Staged new file detected"                  test_staged_new_file_detected
run_test "Staged modification detected"              test_staged_modification_detected
run_test "Untracked file detected"                   test_untracked_file_detected
run_test "THIRD_PARTY_NOTICES.md change — committed" test_notices_file_changed_true
run_test "THIRD_PARTY_NOTICES.md change — staged"    test_notices_staged_change_detected
run_test "URL matching to notices entries"           test_notices_url_matching
run_test "MIT — no false positive from COMMIT"       test_mit_no_false_positive_from_commit
run_test "Excluded paths skipped"                    test_excluded_paths_skipped
run_test "Generated files skipped"                   test_generated_files_skipped
run_test "Sentry copyright not flagged"              test_sentry_copyright_not_flagged
run_test "New license type detected"                 test_new_license_type_detected
run_test "License-only header not flagged"           test_license_only_header_not_flagged
run_test "Binary file skipped"                       test_binary_file_skipped
run_test "Modified vendor-path file — attribution changed" test_modified_vendor_path_file_attribution_changed
run_test "Modified vendored file — no attribution change" test_modified_vendored_file_no_attribution_change
run_test "Modified first-party file — Sentry license header not flagged" test_modified_first_party_license_header_not_flagged
run_test "Unstaged modification detected"             test_unstaged_modification_detected
run_test "Multiple candidates in single run"          test_multiple_candidates
run_test "Missing THIRD_PARTY_NOTICES.md"             test_missing_notices_file
run_test "Progressive URL matching"                   test_progressive_url_matching
run_test "NOTICES entry removed"                      test_notices_entry_removed
run_test "NOTICES entry modified"                     test_notices_entry_modified
run_test "Source-file candidate has candidate_type"   test_source_file_has_candidate_type

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
