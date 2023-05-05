package io.sentry.hints;

/**
 * Marker interface for events that have to be backfilled with the event data (contexts, tags, etc.)
 * that is persisted on disk between application launches
 */
public interface Backfillable {
  boolean shouldEnrich();
}
