#!/bin/sh
rm -f sentry.db
cp boot_sentry.db sentry.db
sentry --config=default_config.py upgrade
foreman start
