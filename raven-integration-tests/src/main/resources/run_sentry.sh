#!/bin/sh
rm -f sentry.db
sentry --config=default_config.py upgrade
foreman start
