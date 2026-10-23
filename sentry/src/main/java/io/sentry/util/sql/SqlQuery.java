package io.sentry.util.sql;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Result of {@link SqlQueryAnalyzer#analyze(String, SqlDialect)}. */
@ApiStatus.Internal
public final class SqlQuery {

  static final @NotNull SqlQuery EMPTY = new SqlQuery(null, null, null, null, null);

  private final @Nullable String queryText;
  private final @Nullable String querySummary;
  private final @Nullable String operationName;
  private final @Nullable String collectionName;
  private final @Nullable String storedProcedureName;

  SqlQuery(
      final @Nullable String queryText,
      final @Nullable String querySummary,
      final @Nullable String operationName,
      final @Nullable String collectionName,
      final @Nullable String storedProcedureName) {
    this.queryText = queryText;
    this.querySummary = querySummary;
    this.operationName = operationName;
    this.collectionName = collectionName;
    this.storedProcedureName = storedProcedureName;
  }

  /**
   * Returns the sanitized statement, or {@code null} if the statement could not be sanitized
   * safely.
   */
  public @Nullable String getQueryText() {
    return queryText;
  }

  /**
   * Returns a low-cardinality summary of operations and targets, such as {@code SELECT users}, or
   * {@code null} if nothing was recognized.
   */
  public @Nullable String getQuerySummary() {
    return querySummary;
  }

  /** Returns the operation, such as {@code SELECT}, or {@code null}. */
  public @Nullable String getOperationName() {
    return operationName;
  }

  /** Returns the single table or collection the statement acts on, or {@code null}. */
  public @Nullable String getCollectionName() {
    return collectionName;
  }

  /** Returns the stored procedure invoked by the statement, or {@code null}. */
  public @Nullable String getStoredProcedureName() {
    return storedProcedureName;
  }
}
