#!/bin/bash

# Disable mobile data first
echo "Disabling mobile data..."
adb shell svc data disable

# Loop 8 times to toggle Wi-Fi
for i in {1..8}
do
  echo "[$i] Disabling Wi-Fi..."
  adb shell svc wifi disable
  sleep 2

  echo "[$i] Enabling Wi-Fi..."
  adb shell svc wifi enable
  sleep 6
done
# Turn mobile data back on
adb shell svc data enable
echo "Done flapping Wi-Fi 8 times."
