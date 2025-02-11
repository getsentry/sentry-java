package io.sentry;

import io.sentry.protocol.SentryId;
import io.sentry.protocol.TransactionNameSource;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
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

  @ApiStatus.Internal
  void setName(@NotNull String name, @NotNull TransactionNameSource transactionNameSource);

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
   * Starts a child Span.
   *
   * @param operation - new span operation name
   * @param description - the span description
   * @param timestamp - the start timestamp of the span
   * @return a new transaction span
   */
  @NotNull
  ISpan startChild(
      @NotNull String operation, @Nullable String description, @Nullable SentryDate timestamp);

  /**
   * Returns if the profile of a transaction is sampled.
   *
   * @return profile is sampled
   */
  @Nullable
  Boolean isProfileSampled();

  /**
   * Returns the latest span that is not finished.
   *
   * @return span or null if not found.
   */
  @Nullable
  ISpan getLatestActiveSpan();

  /** Schedules when transaction should be automatically finished. */
  void scheduleFinish();

  /**
   * Force finishes the transaction and it's child spans with the specified status. If the
   * transaction is already finished this is a no-op.
   *
   * @param status The status to set the unfinished child spans / transaction to.
   * @param dropIfNoChildren true, if the transaction should be dropped when it e.g. contains no
   *     child spans. Usually true, but can be set to falseS for situations were the transaction and
   *     profile provide crucial context (e.g. ANRs)
   * @param hint An optional hint to pass down to the client/transport layer
   */
  @ApiStatus.Internal
  void forceFinish(@NotNull final SpanStatus status, boolean dropIfNoChildren, @Nullable Hint hint);

  @ApiStatus.Internal
  void finish(
      @Nullable SpanStatus status,
      @Nullable SentryDate timestamp,
      boolean dropIfNoChildren,
      @Nullable Hint hint);

  @NotNull
  SentryId getEventId();
}
