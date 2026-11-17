#!/bin/sh
# Writes the Compose UI versions from gradle/compose-ui-versions.json to the step's outputs, as a
# JSON list for the Compose UI matrix.
set -eu

versions=$(jq -c -e 'if type == "array" and length > 0 then . else error("expected a non-empty list") end' \
  gradle/compose-ui-versions.json)
printf 'versions=%s\n' "$versions" >> "$GITHUB_OUTPUT"
