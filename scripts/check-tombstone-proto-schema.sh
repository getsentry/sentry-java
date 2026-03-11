#!/usr/bin/env bash
set -euo pipefail

TRACKED_COMMIT="981d145117e8992842cdddee555c57e60c7a220a"

# tail -n +2 to remove the magic anti-XSSI prefix from the Gitiles JSON response
LATEST_COMMIT=$(curl -sf \
  'https://android.googlesource.com/platform/system/core/+log/refs/heads/main/debuggerd/proto/tombstone.proto?format=JSON' \
  | tail -n +2 \
  | jq -r '.log[0].commit')

if [ -z "$LATEST_COMMIT" ] || [ "$LATEST_COMMIT" = "null" ]; then
  echo "ERROR: Failed to fetch latest commit from Gitiles" >&2
  exit 1
fi

echo "Tracked commit: $TRACKED_COMMIT"
echo "Latest commit:  $LATEST_COMMIT"

if [ "$LATEST_COMMIT" != "$TRACKED_COMMIT" ]; then
  echo "Schema has been updated! Latest: https://android.googlesource.com/platform/system/core/+/${LATEST_COMMIT}/debuggerd/proto/tombstone.proto"
  exit 1
fi

echo "Schema is up to date."
