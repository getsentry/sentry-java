package io.sentry.opentelemetry;

import io.sentry.protocol.TransactionNameSource;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class OtelSpanInfo {

  private final @NotNull String op;
  private final @NotNull String description;
  private final @NotNull TransactionNameSource transactionNameSource;

  private final @NotNull Map<String, Object> dataFields;

  public OtelSpanInfo(
      final @NotNull String op,
      final @NotNull String description,
      final @NotNull TransactionNameSource transactionNameSource,
      final @NotNull Map<String, Object> dataFields) {
    this.op = op;
    this.description = description;
    this.transactionNameSource = transactionNameSource;
    this.dataFields = dataFields;
  }

  public OtelSpanInfo(
      final @NotNull String op,
      final @NotNull String description,
      final @NotNull TransactionNameSource transactionNameSource) {
    this.op = op;
    this.description = description;
    this.transactionNameSource = transactionNameSource;
    this.dataFields = new HashMap<>();
  }

  public @NotNull String getOp() {
    return op;
  }

  public @NotNull String getDescription() {
    return description;
  }

  public @NotNull TransactionNameSource getTransactionNameSource() {
    return transactionNameSource;
  }

  public @NotNull Map<String, Object> getDataFields() {
    return dataFields;
  }

  public void addDataField(final @NotNull String key, final @NotNull Object value) {
    dataFields.put(key, value);
  }
}
