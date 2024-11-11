#!/usr/bin/env bash

python3 test/system-test-sentry-server.py > sentry-mock-server.txt 2>&1 &
echo $! > sentry-mock-server.pid
