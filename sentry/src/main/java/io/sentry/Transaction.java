package io.sentry;

import java.util.Date;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Transaction extends SentryBaseEvent<TransactionContexts> implements ISpan, Cloneable {
  /** The transaction name. */
  private @Nullable String transaction;

  @SuppressWarnings("UnusedVariable")
  private @NotNull final String type = "transaction";

  @SuppressWarnings("UnusedVariable")
  private @NotNull final String platform = "java";

  private @NotNull Date startTimestamp;
  /** End timestamp. */
  private @Nullable Date timestamp;

  /** Creates unnamed transaction. */
  public Transaction() {
    this(null, null);
  }

  public Transaction(final @Nullable TransactionContexts contexts) {
    this(null, contexts);
  }

  /**
   * Creates transaction with name and contexts.
   *
   * @param name - transaction name
   * @param contexts - transaction contexts
   */
  public Transaction(final @Nullable String name, final @Nullable TransactionContexts contexts) {
    this.transaction = name;
    this.startTimestamp = DateUtils.getCurrentDateTime();
    if (contexts == null) {
      this.setContexts(new TransactionContexts());
    } else {
      this.setContexts(contexts);
    }
  }

  /**
   * Sets transaction name.
   *
   * @param name - transaction name
   */
  public void setName(final @Nullable String name) {
    this.transaction = name;
  }

  @Override
  public ISpan startChild() {
    return null;
  }

  @Override
  public String toTraceparent() {
    return String.format("%s-%s", getContexts().getTrace().getTraceId(), getContexts().getTrace().getSpanId());
  }

  @Override
  public void finish() {
    this.timestamp = DateUtils.getCurrentDateTime();
  }

  public @Nullable String getTransaction() {
    return transaction;
  }

  public @NotNull Date getStartTimestamp() {
    return startTimestamp;
  }

  public @Nullable Date getTimestamp() {
    return timestamp;
  }

  @Override
  protected Transaction clone() throws CloneNotSupportedException {
    final Transaction clone = (Transaction) super.clone();
    clone.setContexts(this.getContexts().clone());
    return clone;
  }
}
