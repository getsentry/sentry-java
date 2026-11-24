package io.sentry.android.core.hang;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** A collector, the writer of its values into the record, and its optional store. */
@ApiStatus.Internal
public final class HangCollectorSetup<T> {
  final @NotNull HangDataCollector<T> collector;
  final @NotNull HangRecordWriter<T> writer;
  final @Nullable HangSampleStore<T> store;

  public HangCollectorSetup(
      final @NotNull HangDataCollector<T> collector,
      final @NotNull HangRecordWriter<T> writer,
      final @Nullable HangSampleStore<T> store) {
    this.collector = collector;
    this.writer = writer;
    this.store = store;
  }
}
