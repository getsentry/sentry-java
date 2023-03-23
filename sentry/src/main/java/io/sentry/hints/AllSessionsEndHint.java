package io.sentry.hints;

/** An aggregator hint which marks envelopes to end all pending sessions */
public final class AllSessionsEndHint implements SessionEnd, PreviousSessionEnd {}
