#!/bin/bash

# Loop 4 times to toggle Wi-Fi
for i in {1..4}
do
  echo "[$i] Turning screen off..."
  adb shell input keyevent 223
  sleep 5

  echo "[$i] Turning screen on..."
  adb shell input keyevent 224
  sleep 5
done

echo "Done flapping Screen 4 times."
