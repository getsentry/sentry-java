#!/usr/bin/env bash

./test/system-test-run.sh "sentry-samples-spring-boot" "0" "true" "0"
./test/system-test-run.sh "sentry-samples-spring-boot-opentelemetry-noagent" "0" "true" "0"
./test/system-test-run.sh "sentry-samples-spring-boot-opentelemetry" "1" "true" "0"
./test/system-test-run.sh "sentry-samples-spring-boot-opentelemetry" "1" "false" "0"
./test/system-test-run.sh "sentry-samples-spring-boot-webflux-jakarta" "0" "true" "0"
./test/system-test-run.sh "sentry-samples-spring-boot-webflux" "0" "true" "0"
./test/system-test-run.sh "sentry-samples-spring-boot-jakarta" "0" "true" "0"
./test/system-test-run.sh "sentry-samples-spring-boot-jakarta-opentelemetry-noagent" "0" "true" "0"
./test/system-test-run.sh "sentry-samples-spring-boot-jakarta-opentelemetry" "1" "true" "0"
./test/system-test-run.sh "sentry-samples-spring-boot-jakarta-opentelemetry" "1" "false" "0"
./test/system-test-run.sh "sentry-samples-console" "0" "true" "0"
