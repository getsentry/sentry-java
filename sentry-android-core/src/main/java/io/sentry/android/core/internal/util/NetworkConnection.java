package io.sentry.android.core.internal.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/** The connection type and the generation of the cellular network technology, if any. */
@ApiStatus.Internal
public final class NetworkConnection {
  /** Maps to the {@code network.connection.type} attribute, {@code null} when unknown. */
  public final @Nullable String type;

  /**
   * Maps to the {@code network.connection.effective_type} attribute, for example {@code 5g}. {@code
   * null} when the connection is not cellular or the technology is unknown.
   */
  public final @Nullable String effectiveType;

  NetworkConnection(final @Nullable String type, final @Nullable String effectiveType) {
    this.type = type;
    this.effectiveType = effectiveType;
  }
}
