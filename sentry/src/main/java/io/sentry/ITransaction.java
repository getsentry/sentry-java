package io.sentry;

import io.sentry.protocol.Contexts;
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
   * Returns if transaction is sampled.
   *
   * @return is sampled
   */
  @Nullable
  Boolean isSampled();

  /**
   * Returns if the profile of a transaction is sampled.
   *
   * @return profile is sampled
   */
  @Nullable
  Boolean isProfileSampled();

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

  @ApiStatus.Internal
  void setContext(@NotNull String key, @NotNull Object context);

  @ApiStatus.Internal
  @NotNull
  Contexts getContexts();
}
