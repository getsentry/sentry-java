package io.sentry;

import io.sentry.protocol.SentryId;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public interface ITransaction extends ISpan {

  /**
   * Sets transaction name.
   *
   * @param name - transaction name
   */
  void setName(@NotNull String name);

  /**
   * Returns transaction name.
   *
   * @return transaction name
   */
  @NotNull
  String getName();

  @NotNull
  @TestOnly
  List<Span> getSpans();

  /**
   * Returns if transaction is sampled.
   *
   * @return is sampled
   */
  @Nullable
  Boolean isSampled();

  /**
   * Returns the latest span that is not finished.
   *
   * @return span or null if not found.
   */
  @Nullable
  Span getLatestActiveSpan();

  /**
   * Returns transaction's event id.
   *
   * @return the event id
   */
  @NotNull
  SentryId getEventId();

  /** Schedules when transaction should be automatically finished. */
  void scheduleFinish();
}
