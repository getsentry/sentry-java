package io.sentry.opentelemetry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.semconv.SemanticAttributes;
import io.sentry.protocol.TransactionNameSource;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SpanDescriptionExtractor {

  // TODO [POTEL] remove these method overloads and pass in SpanData instead (span.toSpanData())
  @SuppressWarnings("deprecation")
  public @NotNull OtelSpanInfo extractSpanInfo(
      final @NotNull SpanData otelSpan, final @Nullable OtelSpanWrapper sentrySpan) {
    OtelSpanInfo spanInfo = extractSpanDescription(otelSpan, sentrySpan);

    final @Nullable Long threadId = otelSpan.getAttributes().get(SemanticAttributes.THREAD_ID);
    if (threadId != null) {
      spanInfo.addDataField("thread.id", threadId);
    }

    final @Nullable String threadName =
        otelSpan.getAttributes().get(SemanticAttributes.THREAD_NAME);
    if (threadName != null) {
      spanInfo.addDataField("thread.name", threadName);
    }

    final @Nullable String dbSystem = otelSpan.getAttributes().get(SemanticAttributes.DB_SYSTEM);
    if (dbSystem != null) {
      spanInfo.addDataField("db.system", dbSystem);
    }

    final @Nullable String dbName = otelSpan.getAttributes().get(SemanticAttributes.DB_NAME);
    if (dbName != null) {
      spanInfo.addDataField("db.name", dbName);
    }

    return spanInfo;
  }

  @SuppressWarnings("deprecation")
  private OtelSpanInfo extractSpanDescription(
      final @NotNull SpanData otelSpan, final @Nullable OtelSpanWrapper sentrySpan) {
    //TODO POTEL add OTEL span attributes to SpanData!
    final @NotNull Attributes attributes = otelSpan.getAttributes();
    final @NotNull Map<String, Object> dataFields = new HashMap<>();

    otelSpan.getAttributes().forEach((key, value) -> {
      dataFields.put(key.getKey(), value);
    });

    final @Nullable String httpMethod = attributes.get(SemanticAttributes.HTTP_METHOD);
    if (httpMethod != null) {
      return descriptionForHttpMethod(otelSpan, httpMethod, dataFields);
    }

    final @Nullable String httpRequestMethod =
        attributes.get(SemanticAttributes.HTTP_REQUEST_METHOD);
    if (httpRequestMethod != null) {
      return descriptionForHttpMethod(otelSpan, httpRequestMethod, dataFields);
    }

    final @Nullable String dbSystem = attributes.get(SemanticAttributes.DB_SYSTEM);
    if (dbSystem != null) {
      return descriptionForDbSystem(otelSpan, dataFields);
    }

    final @NotNull String name = otelSpan.getName();
    final @Nullable String maybeDescription =
        sentrySpan != null ? sentrySpan.getDescription() : name;
    final @NotNull String description = maybeDescription != null ? maybeDescription : name;
    return new OtelSpanInfo(name, description, TransactionNameSource.CUSTOM, dataFields);
  }

  @SuppressWarnings("deprecation")
  private OtelSpanInfo descriptionForHttpMethod(
      final @NotNull SpanData otelSpan, final @NotNull String httpMethod, @NotNull Map<String, Object> dataFields) {
    final @NotNull String name = otelSpan.getName();
    final @NotNull SpanKind kind = otelSpan.getKind();
    final @NotNull StringBuilder opBuilder = new StringBuilder("http");
    final @NotNull Attributes attributes = otelSpan.getAttributes();
    dataFields.put("http.request.method", httpMethod);

    if (SpanKind.CLIENT.equals(kind)) {
      opBuilder.append(".client");
    } else if (SpanKind.SERVER.equals(kind)) {
      opBuilder.append(".server");
    }
    final @Nullable String httpTarget = attributes.get(SemanticAttributes.HTTP_TARGET);
    final @Nullable String httpRoute = attributes.get(SemanticAttributes.HTTP_ROUTE);
    @Nullable String httpPath = httpRoute;
    if (httpPath == null) {
      httpPath = httpTarget;
    }
    final @NotNull String op = opBuilder.toString();

    final @Nullable Long httpStatusCode =
        attributes.get(SemanticAttributes.HTTP_RESPONSE_STATUS_CODE);
    if (httpStatusCode != null) {
      dataFields.put("http.response.status_code", httpStatusCode);
    }

    final @Nullable String serverAddress = attributes.get(SemanticAttributes.SERVER_ADDRESS);
    if (serverAddress != null) {
      dataFields.put("server.address", serverAddress);
    }

    final @Nullable String urlFull = attributes.get(SemanticAttributes.URL_FULL);
    if (urlFull != null) {
      dataFields.put("url.full", urlFull);
      if (httpPath == null) {
        httpPath = urlFull;
      }
    }

    if (httpPath == null) {
      return new OtelSpanInfo(op, name, TransactionNameSource.CUSTOM, dataFields);
    }

    final @NotNull String description = httpMethod + " " + httpPath;
    final @NotNull TransactionNameSource transactionNameSource =
        httpRoute != null ? TransactionNameSource.ROUTE : TransactionNameSource.URL;

    return new OtelSpanInfo(op, description, transactionNameSource, dataFields);
  }

  @SuppressWarnings("deprecation")
  private OtelSpanInfo descriptionForDbSystem(final @NotNull SpanData otelSpan, @NotNull Map<String, Object> dataFields) {
    final @NotNull Attributes attributes = otelSpan.getAttributes();
    @Nullable String dbStatement = attributes.get(SemanticAttributes.DB_STATEMENT);
    @NotNull String description = dbStatement != null ? dbStatement : otelSpan.getName();
    return new OtelSpanInfo("db", description, TransactionNameSource.TASK, dataFields);
  }
}
