package io.sentry.android.core.hang;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

// Not a BiConsumer, which needs API 24.
/** Puts one value of a collector into the record. */
@ApiStatus.Internal
public interface HangRecordWriter<T> {
  void write(@NotNull T sample, @NotNull HangRecord.Builder record);
}
