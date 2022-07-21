package io.sentry;

import io.sentry.protocol.SentryId;
import io.sentry.protocol.TransactionNameSource;
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

  /**
   * Returns the source of the transaction name.
   *
   * @return transaction name source
   */
  @NotNull
  TransactionNameSource getTransactionNameSource();

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

  @Nullable
  TracesSamplingDecision getSamplingDecision();

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
