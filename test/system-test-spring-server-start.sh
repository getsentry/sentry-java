#!/usr/bin/env bash

readonly SAMPLE_MODULE=$1
SENTRY_DSN="http://502f25099c204a2fbf4cb16edc5975d1@localhost:8000/0" java -jar sentry-samples/${SAMPLE_MODULE}/build/libs/${SAMPLE_MODULE}-0.0.1-SNAPSHOT.jar
