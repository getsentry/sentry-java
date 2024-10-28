#!/usr/bin/env bash

readonly SAMPLE_MODULE=$1
readonly JAVA_AGENT=$2

JAVA_AGENT_STRING=""

if [ "$JAVA_AGENT" = 1 ]; then
  JAVA_AGENT_STRING="-javaagent:./sentry-opentelemetry/sentry-opentelemetry-agent/build/libs/sentry-opentelemetry-agent-8.0.0-beta.1.jar"
  echo "Using Java Agent"
fi

SENTRY_DSN="http://502f25099c204a2fbf4cb16edc5975d1@localhost:8000/0" SENTRY_TRACES_SAMPLE_RATE=1.0 OTEL_TRACES_EXPORTER=none OTEL_METRICS_EXPORTER=none OTEL_LOGS_EXPORTER=none java ${JAVA_AGENT_STRING} -jar sentry-samples/${SAMPLE_MODULE}/build/libs/${SAMPLE_MODULE}-0.0.1-SNAPSHOT.jar
