package io.sentry.opentelemetry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import io.opentelemetry.semconv.incubating.HttpIncubatingAttributes;
import io.sentry.protocol.TransactionNameSource;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SpanDescriptionExtractor {

  @SuppressWarnings("deprecation")
  public @NotNull OtelSpanInfo extractSpanInfo(
      final @NotNull SpanData otelSpan, final @Nullable IOtelSpanWrapper sentrySpan) {
    final @NotNull Attributes attributes = otelSpan.getAttributes();

    final @Nullable String httpMethod = attributes.get(HttpAttributes.HTTP_REQUEST_METHOD);
    if (httpMethod != null) {
      return descriptionForHttpMethod(otelSpan, httpMethod);
    }

    final @Nullable String dbSystem = attributes.get(DbIncubatingAttributes.DB_SYSTEM);
    if (dbSystem != null) {
      return descriptionForDbSystem(otelSpan);
    }

    final @NotNull String name = otelSpan.getName();
    final @Nullable String maybeDescription =
        sentrySpan != null ? sentrySpan.getDescription() : name;
    final @NotNull String description = maybeDescription != null ? maybeDescription : name;
    return new OtelSpanInfo(name, description, TransactionNameSource.CUSTOM);
  }

  @SuppressWarnings("deprecation")
  private OtelSpanInfo descriptionForHttpMethod(
      final @NotNull SpanData otelSpan, final @NotNull String httpMethod) {
    final @NotNull String name = otelSpan.getName();
    final @NotNull SpanKind kind = otelSpan.getKind();
    final @NotNull StringBuilder opBuilder = new StringBuilder("http");
    final @NotNull Attributes attributes = otelSpan.getAttributes();

    if (SpanKind.CLIENT.equals(kind)) {
      opBuilder.append(".client");
    } else if (SpanKind.SERVER.equals(kind)) {
      opBuilder.append(".server");
    } else {
      // we cannot be certain that a root span is a server span as it might simply be a client span
      // without parent
      if (!isRootSpan(otelSpan)) {
        opBuilder.append(".client");
      }
    }
    final @Nullable String httpTarget = attributes.get(HttpIncubatingAttributes.HTTP_TARGET);
    final @Nullable String httpRoute = attributes.get(HttpAttributes.HTTP_ROUTE);
    @Nullable String httpPath = httpRoute;
    if (httpPath == null) {
      httpPath = httpTarget;
    }
    final @NotNull String op = opBuilder.toString();

    final @Nullable String urlFull = attributes.get(UrlAttributes.URL_FULL);
    if (urlFull != null) {
      if (httpPath == null) {
        httpPath = urlFull;
      }
    }

    final @Nullable String urlPath = attributes.get(UrlAttributes.URL_PATH);
    if (httpPath == null && urlPath != null) {
      httpPath = urlPath;
    }

    if (httpPath == null) {
      return new OtelSpanInfo(op, name, TransactionNameSource.CUSTOM);
    }

    final @NotNull String description = httpMethod + " " + httpPath;
    final @NotNull TransactionNameSource transactionNameSource =
        httpRoute != null ? TransactionNameSource.ROUTE : TransactionNameSource.URL;

    return new OtelSpanInfo(op, description, transactionNameSource);
  }

  private static boolean isRootSpan(SpanData otelSpan) {
    return !otelSpan.getParentSpanContext().isValid() || otelSpan.getParentSpanContext().isRemote();
  }

  @SuppressWarnings("deprecation")
  private OtelSpanInfo descriptionForDbSystem(final @NotNull SpanData otelSpan) {
    final @NotNull Attributes attributes = otelSpan.getAttributes();
    @Nullable String dbStatement = attributes.get(DbIncubatingAttributes.DB_STATEMENT);
    if (dbStatement != null) {
      return new OtelSpanInfo("db", dbStatement, TransactionNameSource.TASK);
    }
    @Nullable String dbQueryText = attributes.get(DbIncubatingAttributes.DB_QUERY_TEXT);
    if (dbQueryText != null) {
      return new OtelSpanInfo("db", dbQueryText, TransactionNameSource.TASK);
    }

    return new OtelSpanInfo("db", otelSpan.getName(), TransactionNameSource.TASK);
  }
}
