#!/usr/bin/env bash
# Reads and writes the role keys in gradle/dependency-matrix.versions.toml (<matrix>-floor,
# <matrix>-stable, and <matrix>-preview; see that file's header) for CI jobs and updater scripts.
#
# Usage:
#   dependency-matrix.sh get MATRIX ROLE
#       Prints the version of MATRIX's ROLE.
#   dependency-matrix.sh set MATRIX ROLE VERSION
#       Sets the version of MATRIX's ROLE.
#   dependency-matrix.sh extra-versions MATRIX
#       Prints the versions to test besides the floor, one "ROLE VERSION" line each: the stable
#       version, then the preview version if it's newer than the stable one.
#   dependency-matrix.sh changed-roles BASE HEAD MATRIX
#       Prints the roles of MATRIX whose versions changed between commits BASE and HEAD.
set -euo pipefail

cd "$(dirname "$0")/../"
catalog="gradle/dependency-matrix.versions.toml"

usage() {
  sed -n '5,14p' "$0" | sed 's/^# \{0,1\}//'
  exit 1
}

key() {
  echo "${1:?Missing MATRIX}-${2:?Missing ROLE}"
}

get() {
  local key version
  key="$(key "$@")"
  version="$(KEY="$key" perl -ne 'print "$1\n" if m/^\Q$ENV{KEY}\E\s*=\s*"([^"]+)"/' "$catalog")"
  if [[ -z "$version" ]]; then
    echo "No version for $key in $catalog" >&2
    exit 1
  fi
  echo "$version"
}

set_version() {
  local key
  key="$(key "$1" "$2")"
  local version="${3:?Missing VERSION}"
  get "$1" "$2" > /dev/null
  KEY="$key" VERSION="$version" perl -pi -e 's/^(\Q$ENV{KEY}\E\s*=\s*")[^"]+(")/${1}$ENV{VERSION}${2}/' "$catalog"
}

# Whether pre-release version $1 is newer than stable version $2. A pre-release precedes the release
# of its base version (1.12.0-RC < 1.12.0), so only its base version is compared. Comparing the full
# versions with `sort -V` would rank 1.12.0-RC above 1.12.0.
is_newer_preview() {
  local base="${1%%-*}"
  [[ "$base" != "$2" && "$(printf '%s\n%s\n' "$base" "$2" | sort -V | tail -n 1)" == "$base" ]]
}

extra_versions() {
  local stable preview
  stable="$(get "${1:?Missing MATRIX}" stable)"
  preview="$(get "$1" preview)"
  echo "stable $stable"
  if is_newer_preview "$preview" "$stable"; then
    echo "preview $preview"
  fi
}

changed_roles() {
  local base="${1:?Missing BASE}" head="${2:?Missing HEAD}" matrix="${3:?Missing MATRIX}"
  git diff --unified=0 "$base" "$head" -- "$catalog" \
    | MATRIX="$matrix" perl -ne 'print "$1\n" if m/^[-+]\Q$ENV{MATRIX}\E-(\w+)\s*=\s*"/' \
    | sort -u
}

case "${1:-}" in
  get) get "${2:-}" "${3:-}" ;;
  set) set_version "${2:-}" "${3:-}" "${4:-}" ;;
  extra-versions) extra_versions "${2:-}" ;;
  changed-roles) changed_roles "${2:-}" "${3:-}" "${4:-}" ;;
  *) usage ;;
esac
