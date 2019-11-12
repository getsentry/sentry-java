package io.sentry.core.hints;

// Marker interface for a capture involving data cached from disk
// This means applying data relevant to the current execution should be avoided
// like applying threads or current app version.
public interface Cached {}
