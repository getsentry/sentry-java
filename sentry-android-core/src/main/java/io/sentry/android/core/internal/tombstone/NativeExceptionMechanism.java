package io.sentry.android.core.internal.tombstone;

import androidx.annotation.NonNull;

/** Mechanism types for native crashes. */
public enum NativeExceptionMechanism {
  TOMBSTONE("tombstone"),
  SIGNAL_HANDLER("signalhandler"),
  TOMBSTONE_MERGED("tombstone_merged");

  private final @NonNull String value;

  NativeExceptionMechanism(@NonNull final String value) {
    this.value = value;
  }

  @NonNull
  public String getValue() {
    return value;
  }
}
