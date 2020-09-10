package io.sentry;

/** the SentryLevel */
public enum SentryLevel {
  /** LOG is used as a level in breadcrumbs from console.log in JS */
  LOG,
  DEBUG,
  INFO,
  WARNING,
  ERROR,
  FATAL
}
