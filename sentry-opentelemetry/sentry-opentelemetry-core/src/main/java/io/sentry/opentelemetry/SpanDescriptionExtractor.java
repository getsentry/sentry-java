package io.sentry.opentelemetry;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.semconv.SemanticAttributes;
import io.sentry.protocol.TransactionNameSource;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SpanDescriptionExtractor {

  @SuppressWarnings("deprecation")
  public @NotNull OtelSpanInfo extractSpanDescription(final @NotNull ReadableSpan otelSpan) {
    final @NotNull String name = otelSpan.getName();

    final @Nullable String httpMethod = otelSpan.getAttribute(SemanticAttributes.HTTP_METHOD);
    if (httpMethod != null) {
      return descriptionForHttpMethod(otelSpan, httpMethod);
    }

    final @Nullable String dbSystem = otelSpan.getAttribute(SemanticAttributes.DB_SYSTEM);
    if (dbSystem != null) {
      return descriptionForDbSystem(otelSpan);
    }

    return new OtelSpanInfo(name, name, TransactionNameSource.CUSTOM);
  }

  @SuppressWarnings("deprecation")
  private OtelSpanInfo descriptionForHttpMethod(
      final @NotNull ReadableSpan otelSpan, final @NotNull String httpMethod) {
    final @NotNull String name = otelSpan.getName();
    final @NotNull SpanKind kind = otelSpan.getKind();
    final @NotNull StringBuilder opBuilder = new StringBuilder("http");

    if (SpanKind.CLIENT.equals(kind)) {
      opBuilder.append(".client");
    } else if (SpanKind.SERVER.equals(kind)) {
      opBuilder.append(".server");
    }
    final @Nullable String httpTarget = otelSpan.getAttribute(SemanticAttributes.HTTP_TARGET);
    final @Nullable String httpRoute = otelSpan.getAttribute(SemanticAttributes.HTTP_ROUTE);
    final @Nullable String httpPath = httpRoute != null ? httpRoute : httpTarget;
    final @NotNull String op = opBuilder.toString();

    if (httpPath == null) {
      return new OtelSpanInfo(op, name, TransactionNameSource.CUSTOM);
    }

    final @NotNull String description = httpMethod + " " + httpPath;
    final @NotNull TransactionNameSource transactionNameSource =
        httpRoute != null ? TransactionNameSource.ROUTE : TransactionNameSource.URL;

    return new OtelSpanInfo(op, description, transactionNameSource);
  }

  private OtelSpanInfo descriptionForDbSystem(final @NotNull ReadableSpan otelSpan) {
    @Nullable String dbStatement = otelSpan.getAttribute(SemanticAttributes.DB_STATEMENT);
    @NotNull String description = dbStatement != null ? dbStatement : otelSpan.getName();
    return new OtelSpanInfo("db", description, TransactionNameSource.TASK);
  }
}
