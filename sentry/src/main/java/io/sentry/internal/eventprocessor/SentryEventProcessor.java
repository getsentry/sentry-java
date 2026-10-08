package io.sentry.internal.eventprocessor;

import io.sentry.EventProcessor;
import org.jetbrains.annotations.ApiStatus;

/** Marker interface for event processors implemented by the Sentry SDK. */
@ApiStatus.Internal
public interface SentryEventProcessor extends EventProcessor {}
