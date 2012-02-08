#!/usr/bin/env bash

java -classpath /Users/kcochrane/github/raven-java/out/production/raven-java:../lib/log4j-1.2.16.jar:../lib/httpmime-4.1.2.jar:../lib/commons-codec-1.4.jar:../lib/httpcore-4.1.2.jar:../lib/httpclient-4.1.2.jar:../lib/commons-logging-1.1.1.jar:../lib/json_simple-1.1.jar net.kencochrane.sentry.SentryExample log4j_configuration.txt
