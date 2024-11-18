#!/usr/bin/env bash

readonly SAMPLE_MODULE=$1
readonly JAVA_AGENT=$2
readonly JAVA_AGENT_AUTO_INIT=$3

test/system-test-sentry-server-start.sh
MOCK_SERVER_PID=$(cat sentry-mock-server.pid)
echo "started mock server ${SAMPLE_MODULE}-${JAVA_AGENT}-${JAVA_AGENT_AUTO_INIT} with PID ${MOCK_SERVER_PID}"

test/system-test-spring-server-start.sh "${SAMPLE_MODULE}" "${JAVA_AGENT}" "${JAVA_AGENT_AUTO_INIT}"
SUT_PID=$(cat spring-server.pid)
echo "started spring server ${SAMPLE_MODULE}-${JAVA_AGENT}-${JAVA_AGENT_AUTO_INIT} with PID ${SUT_PID}"

test/wait-for-spring.sh

./gradlew :sentry-samples:${SAMPLE_MODULE}:systemTest

echo "killing mock server ${SAMPLE_MODULE}-${JAVA_AGENT}-${JAVA_AGENT_AUTO_INIT} with PID ${MOCK_SERVER_PID}"
kill $SUT_PID
echo "killing spring server ${SAMPLE_MODULE}-${JAVA_AGENT}-${JAVA_AGENT_AUTO_INIT} with PID ${SUT_PID}"
kill $MOCK_SERVER_PID
