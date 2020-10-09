package io.sentry;

import io.sentry.protocol.SentryId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;

public final class Transaction extends SentryBaseEvent<TransactionContexts> {
  /**
   * The transaction name.
   */
  private @Nullable String transaction;
  @SuppressWarnings("UnusedVariable")
  private @NotNull final String type = "transaction";
  @SuppressWarnings("UnusedVariable")
  private @NotNull final String platform = "java";
  private @NotNull Date startTimestamp;
  /**
   * End timestamp.
   */
  private @Nullable Date timestamp;

  public Transaction(@Nullable String transaction) {
    this.transaction = transaction;
    this.startTimestamp = DateUtils.getCurrentDateTime();
    final TransactionContexts contexts = new TransactionContexts();
    final Trace trace = new Trace();
    trace.setTraceId(new SentryId());
    trace.setSpanId(new SpanId());
    contexts.setTrace(trace);
    this.setContexts(contexts);
  }

  public Transaction() {
    this(null);
  }

  public void finish() {
    this.timestamp = DateUtils.getCurrentDateTime();
  }

  public String getTransaction() {
    return transaction;
  }

  public Date getStartTimestamp() {
    return startTimestamp;
  }

  public @Nullable Date getTimestamp() {
    return timestamp;
  }
}
