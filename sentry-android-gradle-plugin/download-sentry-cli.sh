#!/bin/bash
cd $(dirname "$0")
REPO=getsentry/sentry-cli
VERSION=1.37.0
PLATFORMS="Darwin-x86_64 Linux-i686 Linux-x86_64 Windows-i686"

rm -f src/main/resources/bin/sentry-cli-*
for plat in $PLATFORMS; do
  suffix=''
  if [[ $plat == *"Windows"* ]]; then
    suffix='.exe'
  fi
  echo "${plat}"
  download_url=https://github.com/$REPO/releases/download/$VERSION/sentry-cli-${plat}${suffix}
  fn="src/main/resources/bin/sentry-cli-${plat}${suffix}"
  curl -SL --progress-bar "$download_url" -o "$fn"
  chmod +x "$fn"
done
