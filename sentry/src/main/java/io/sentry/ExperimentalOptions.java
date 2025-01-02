package io.sentry;

import io.sentry.protocol.SdkVersion;
import org.jetbrains.annotations.Nullable;

/**
 * Experimental options for new features, these options are going to be promoted to SentryOptions
 * before GA.
 *
 * <p>Beware that experimental options can change at any time.
 */
public final class ExperimentalOptions {

  public ExperimentalOptions(final boolean empty, final @Nullable SdkVersion sdkVersion) {}
}
