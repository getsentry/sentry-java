#!/usr/bin/env bash
# Keeps the patched OpenTelemetry SQL grammar in sync with upstream (README D36, option 06).
#
#   sentry/src/main/jflex/SqlSanitizer.jflex   our patched grammar, the only file JFlex compiles
#   sentry/otel-upstream/SqlSanitizer.jflex    untouched upstream copy, the merge base
#   sentry/otel-upstream/UPSTREAM              repository, path, commit and sha256 of the copy
#
# Usage:
#   scripts/sync-otel-sql-grammar.sh <commit>   three-way merge of upstream <commit> into ours
#   scripts/sync-otel-sql-grammar.sh --check    verify the copy against its recorded sha256 (offline)
#   scripts/sync-otel-sql-grammar.sh --size     print the size of our patch against the copy
#
# A sync merges upstream's changes since the recorded commit into our patched grammar with
# `git merge-file`. Conflicts are left as conflict markers in the patched grammar; resolve them,
# then run the SQL analyzer tests. Exit status: 0 on success, 1 on conflicts or a failed check,
# 2 on usage or download errors.
set -euo pipefail

root="$(git rev-parse --show-toplevel)"
upstream_dir="$root/sentry/otel-upstream"
copy="$upstream_dir/SqlSanitizer.jflex"
meta="$upstream_dir/UPSTREAM"
ours="$root/sentry/src/main/jflex/SqlSanitizer.jflex"

die() {
  echo "error: $*" >&2
  exit 2
}

meta_value() {
  sed -n "s/^$1=//p" "$meta"
}

sha256() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | cut -d' ' -f1
  else
    shasum -a 256 "$1" | cut -d' ' -f1
  fi
}

check() {
  local expected actual
  expected="$(meta_value sha256)"
  actual="$(sha256 "$copy")"
  if [[ "$expected" != "$actual" ]]; then
    echo "check failed: $copy does not match the sha256 in $meta" >&2
    echo "  recorded: $expected" >&2
    echo "  actual:   $actual" >&2
    echo "The upstream copy must not be edited by hand; change $ours instead." >&2
    return 1
  fi
  echo "ok: upstream copy matches commit $(meta_value commit)"
}

size() {
  # Lines our patch adds and removes relative to the upstream copy. The license header and the
  # package/import rewrite count as part of the patch.
  local added removed
  added="$(diff -U0 "$copy" "$ours" | grep -c '^+[^+]' || true)"
  removed="$(diff -U0 "$copy" "$ours" | grep -c '^-[^-]' || true)"
  echo "patch size against upstream $(meta_value commit): +$added -$removed lines"
}

sync() {
  local commit="$1" repository path old_commit tmp new conflicts
  repository="$(meta_value repository)"
  path="$(meta_value path)"
  old_commit="$(meta_value commit)"
  check >/dev/null || die "the upstream copy is modified; restore it before syncing"

  tmp="$(mktemp -d)"
  # Expanded now: $tmp is local and gone by the time the EXIT trap runs.
  # shellcheck disable=SC2064
  trap "rm -rf '$tmp'" EXIT
  new="$tmp/SqlSanitizer.jflex"
  curl -fsSL "https://raw.githubusercontent.com/$repository/$commit/$path" -o "$new" ||
    die "could not download $path at $commit from $repository"

  # Our header links the upstream file at the vendored commit; point it at the new one.
  if [[ "$commit" != "$old_commit" ]]; then
    sed -i.bak "s|/blob/$old_commit/|/blob/$commit/|" "$ours" && rm -f "$ours.bak"
  fi

  set +e
  git merge-file -L "sentry (patched)" -L "upstream $old_commit" -L "upstream $commit" \
    "$ours" "$copy" "$new"
  conflicts=$?
  set -e
  # git merge-file returns the number of conflicts, or a negative value (exit status >= 128) on error.
  if ((conflicts >= 128)); then
    die "git merge-file failed"
  fi

  cp "$new" "$copy"
  sed -i.bak -e "s/^commit=.*/commit=$commit/" -e "s/^sha256=.*/sha256=$(sha256 "$copy")/" "$meta"
  rm -f "$meta.bak"

  echo "synced $path: $old_commit -> $commit"
  size
  if ((conflicts > 0)); then
    echo "$conflicts conflict(s) in $ours: resolve the markers, then run" >&2
    echo "  ./gradlew :sentry:test --tests 'io.sentry.util.sql.*'" >&2
    return 1
  fi
  echo "no conflicts; run ./gradlew :sentry:test --tests 'io.sentry.util.sql.*'"
}

case "${1:-}" in
  --check) check ;;
  --size) size ;;
  "" | -h | --help) sed -n '2,17p' "$0" | sed 's/^# \{0,1\}//' ;;
  -*) die "unknown option: $1" ;;
  *) sync "$1" ;;
esac
