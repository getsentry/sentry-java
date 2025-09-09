#!/bin/bash

# Script to get the next PR number for a GitHub repository
# Based on: https://github.com/ichard26/next-pr-number/blob/main/app.py

set -euo pipefail

# GraphQL query to get the last issue/PR/discussion number
GRAPHQL_QUERY='
query getLastIssueNumber($owner: String!, $name: String!) {
  repository(owner: $owner, name: $name) {
    discussions(orderBy: {field: CREATED_AT, direction: DESC}, first: 1) {
      nodes {
        number
      }
    }
    issues(orderBy: {field: CREATED_AT, direction: DESC}, first: 1) {
      nodes {
        number
      }
    }
    pullRequests(orderBy: {field: CREATED_AT, direction: DESC}, first: 1) {
      nodes {
        number
      }
    }
  }
}'

usage() {
    echo "Usage: $0 <owner> <repository>"
    echo "Example: $0 getsentry sentry"
    echo ""
    echo "Requires: gh CLI tool to be installed and authenticated"
    echo "Run 'gh auth login' to authenticate if needed"
    exit 1
}

# Check arguments
if [[ $# -ne 2 ]]; then
    usage
fi

OWNER="$1"
REPO="$2"

# Check if gh CLI is available
if ! command -v gh &> /dev/null; then
    echo "Error: gh CLI is not installed"
    echo "Please install it from https://cli.github.com/"
    exit 1
fi

# Check if gh is authenticated
if ! gh auth status &> /dev/null; then
    echo "Error: gh CLI is not authenticated"
    echo "Please run 'gh auth login' to authenticate"
    exit 1
fi

# Make GraphQL request using gh
response=$(gh api graphql -f query="$GRAPHQL_QUERY" -f owner="$OWNER" -f name="$REPO")

# Check if the request was successful
if [[ $? -ne 0 ]]; then
    echo "Error: Failed to make GraphQL request"
    exit 1
fi

# Parse the response and extract the highest number
highest_number=$(echo "$response" | jq -r '
    .data.repository |
    [
        (.discussions.nodes[]? | .number // 0),
        (.issues.nodes[]? | .number // 0),
        (.pullRequests.nodes[]? | .number // 0)
    ] |
    max // 0
')

# Calculate next number
if [[ "$highest_number" != "null" && "$highest_number" -gt 0 ]]; then
    next_number=$((highest_number + 1))
    echo "$next_number"
else
    echo "1"
fi
