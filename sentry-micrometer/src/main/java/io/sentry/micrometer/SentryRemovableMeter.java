package io.sentry.micrometer;

interface SentryRemovableMeter {
  void markRemoved();
}
