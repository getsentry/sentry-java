package io.sentry.vendor.otel.sql;

import org.jetbrains.annotations.Nullable;

/**
 * What AutoValue would generate for the vendored {@link SqlQuery}: fields, constructor and getters.
 * Hand-written so that {@code sentry} needs no annotation processor. A property added or removed
 * upstream breaks compilation here, so a sync cannot drift silently.
 */
final class AutoValue_SqlQuery extends SqlQuery {

  private final @Nullable String queryText;
  private final @Nullable String operationName;
  private final @Nullable String collectionName;
  private final @Nullable String storedProcedureName;
  private final @Nullable String querySummary;

  AutoValue_SqlQuery(
      final @Nullable String queryText,
      final @Nullable String operationName,
      final @Nullable String collectionName,
      final @Nullable String storedProcedureName,
      final @Nullable String querySummary) {
    this.queryText = queryText;
    this.operationName = operationName;
    this.collectionName = collectionName;
    this.storedProcedureName = storedProcedureName;
    this.querySummary = querySummary;
  }

  @Override
  public @Nullable String getQueryText() {
    return queryText;
  }

  @Override
  public @Nullable String getOperationName() {
    return operationName;
  }

  @Override
  public @Nullable String getCollectionName() {
    return collectionName;
  }

  @Override
  public @Nullable String getStoredProcedureName() {
    return storedProcedureName;
  }

  @Override
  public @Nullable String getQuerySummary() {
    return querySummary;
  }
}
