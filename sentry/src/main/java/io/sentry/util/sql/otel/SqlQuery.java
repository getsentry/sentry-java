// Adapted from OpenTelemetry Instrumentation for Java.
// Copyright The OpenTelemetry Authors.
// Licensed under the Apache License, Version 2.0.
// https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/38e68968be30491d74065230e6bf5cfdcf0c13d8/instrumentation-api-incubator/src/main/java/io/opentelemetry/instrumentation/api/incubator/semconv/db/SqlQuery.java

package io.sentry.util.sql.otel;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Stand-in for upstream's AutoValue {@code SqlQuery}, so the vendored grammar compiles with its
 * {@code SqlQuery.create(...)} calls unchanged. Keeps upstream's summary truncation at 255
 * characters. Operation and collection are dropped: Sentry does not extract them (README D1).
 */
@ApiStatus.Internal
public final class SqlQuery {

  private static final String SQL_CALL = "CALL";
  private static final int QUERY_SUMMARY_MAX_LENGTH = 255;

  private final @Nullable String queryText;
  private final @Nullable String storedProcedureName;
  private final @Nullable String querySummary;

  private SqlQuery(
      final @Nullable String queryText,
      final @Nullable String storedProcedureName,
      final @Nullable String querySummary) {
    this.queryText = queryText;
    this.storedProcedureName = storedProcedureName;
    this.querySummary = querySummary;
  }

  static SqlQuery create(
      final @Nullable String queryText,
      final @Nullable String operationName,
      final @Nullable String collectionName,
      final @Nullable String storedProcedureName,
      final @Nullable String querySummary) {
    return new SqlQuery(queryText, storedProcedureName, truncateQuerySummary(querySummary));
  }

  static SqlQuery create(
      final @Nullable String queryText,
      final @Nullable String operationName,
      final @Nullable String target) {
    final boolean isStoredProcedure =
        SQL_CALL.equals(operationName) || "EXECUTE".equals(operationName);
    return create(queryText, operationName, null, isStoredProcedure ? target : null, null);
  }

  private static @Nullable String truncateQuerySummary(final @Nullable String querySummary) {
    if (querySummary == null || querySummary.length() <= QUERY_SUMMARY_MAX_LENGTH) {
      return querySummary;
    }
    // Truncate at the last space before the limit to avoid cutting in the middle of an identifier.
    final int lastSpace = querySummary.lastIndexOf(' ', QUERY_SUMMARY_MAX_LENGTH);
    if (lastSpace > 0) {
      return querySummary.substring(0, lastSpace);
    }
    return StringUtils.truncate(querySummary, QUERY_SUMMARY_MAX_LENGTH);
  }

  public @Nullable String getQueryText() {
    return queryText;
  }

  public @Nullable String getStoredProcedureName() {
    return storedProcedureName;
  }

  public @Nullable String getQuerySummary() {
    return querySummary;
  }
}
