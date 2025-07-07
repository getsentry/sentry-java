package io.sentry.opentelemetry;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.sentry.ISpan;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class OtelSpanUtils {
  public static <T> void maybeTransferOtelAttribute(
      final @NotNull SpanData otelSpan,
      final @NotNull ISpan sentrySpan,
      final @NotNull AttributeKey<T> key) {
    final @NotNull Attributes attributes = otelSpan.getAttributes();
    final @Nullable T value = attributes.get(key);
    if (value != null) {
      sentrySpan.setData(key.getKey(), value);
    }
  }
}
