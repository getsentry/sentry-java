#!/bin/sh
# Reports a failed scheduled Compose UI matrix run: opens an issue, or comments on the open one so
# repeated daily failures stay in one place. Needs GH_TOKEN, GITHUB_REPOSITORY, GITHUB_SERVER_URL,
# and GITHUB_RUN_ID, which GitHub Actions sets.
set -eu

ISSUE_TITLE='Session Replay: Compose UI matrix fails on the latest Compose UI versions'
export ISSUE_TITLE
run_url="$GITHUB_SERVER_URL/$GITHUB_REPOSITORY/actions/runs/$GITHUB_RUN_ID"
body="The scheduled [Compose UI matrix run]($run_url) failed. A new \`androidx.compose.ui:ui\` release may have changed Compose internals that Session Replay calls. Check the failing jobs for the version."

# --search is fuzzy, so match the exact title afterwards.
existing=$(gh issue list --repo "$GITHUB_REPOSITORY" --state open --search "$ISSUE_TITLE in:title" \
  --json number,title --jq '[.[] | select(.title == env.ISSUE_TITLE)][0].number // empty')

if [ -n "$existing" ]; then
  gh issue comment "$existing" --repo "$GITHUB_REPOSITORY" --body "$body"
else
  gh issue create --repo "$GITHUB_REPOSITORY" --title "$ISSUE_TITLE" --body "$body" \
    --label 'Session Replay' --label 'Jetpack Compose'
fi
