package io.sentry.opentelemetry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import io.opentelemetry.semconv.incubating.HttpIncubatingAttributes;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import io.sentry.SentryOptions;
import io.sentry.protocol.TransactionNameSource;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SpanDescriptionExtractor {

  @SuppressWarnings("deprecation")
  public @NotNull OtelSpanInfo extractSpanInfo(
      final @NotNull SpanData otelSpan,
      final @Nullable IOtelSpanWrapper sentrySpan,
      final @NotNull SentryOptions options) {
    final @NotNull Attributes attributes = otelSpan.getAttributes();

    if (options.isEnableQueueTracing()) {
      final @Nullable String messagingSystem =
          attributes.get(MessagingIncubatingAttributes.MESSAGING_SYSTEM);
      if (messagingSystem != null) {
        return descriptionForMessagingSystem(otelSpan);
      }
    }

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
  private OtelSpanInfo descriptionForMessagingSystem(final @NotNull SpanData otelSpan) {
    final @NotNull Attributes attributes = otelSpan.getAttributes();
    final @NotNull String op = opForMessaging(otelSpan);
    final @Nullable String destination =
        attributes.get(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME);
    final @NotNull String description = destination != null ? destination : otelSpan.getName();
    return new OtelSpanInfo(op, description, TransactionNameSource.TASK);
  }

  @SuppressWarnings("deprecation")
  private @NotNull String opForMessaging(final @NotNull SpanData otelSpan) {
    final @NotNull Attributes attributes = otelSpan.getAttributes();
    // Prefer `messaging.operation.type` (current OTel semconv), fall back to legacy
    // `messaging.operation`. OTel's SpanKind.CONSUMER is overloaded for both `receive` and
    // `process`, so attribute-first mapping is required. SpanKind is used only as a last resort.
    @Nullable
    String operationType = attributes.get(MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE);
    if (operationType == null) {
      operationType = attributes.get(MessagingIncubatingAttributes.MESSAGING_OPERATION);
    }
    if (operationType != null) {
      switch (operationType) {
        case "publish":
        case "send":
          return "queue.publish";
        case "create":
          return "queue.create";
        case "receive":
          return "queue.receive";
        case "process":
        case "deliver":
          return "queue.process";
        case "settle":
          return "queue.settle";
        default:
          // fall through to SpanKind mapping
          break;
      }
    }

    final @NotNull SpanKind kind = otelSpan.getKind();
    if (SpanKind.PRODUCER.equals(kind)) {
      return "queue.publish";
    }
    if (SpanKind.CONSUMER.equals(kind)) {
      return "queue.process";
    }
    return "queue";
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
