# Raven Integration Tests

This module provides integration tests for Raven-Java with Sentry. Running these tests is a bit cumbersome, but worth
it.

## 1. Install Sentry
Make sure you have Sentry installed. Follow the instructions in
[Sentry's documentation](http://sentry.readthedocs.org/en/latest/quickstart/index.html#install-sentry) if you haven't.

## 2. Install foreman
[Foreman](http://ddollar.github.com/foreman/) allows you to use a Procfile which we can use to run the HTTP and UDP
services of Sentry in the same terminal.

## 3. Run `src/main/resources/run_sentry.sh` before running the tests
This script will remove any existing `sentry.db` at the *same* location so we can start fresh, replace it with `boot_sentry.db` and then perform a Sentry
upgrade using `default_config.py` as the Sentry configuration. The database is set up with a user with username and password `test`.

Once the Sentry installation is finished, the `Procfile` at the same location will be used to kickstart Sentry HTTP and
UDP services at ports 9500 and 9501 respectively.

## 4. Run the tests
Run the tests like you normally would.