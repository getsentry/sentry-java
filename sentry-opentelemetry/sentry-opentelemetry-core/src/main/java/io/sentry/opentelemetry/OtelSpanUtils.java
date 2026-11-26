package io.sentry.opentelemetry;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import io.sentry.ISpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class OtelSpanUtils {

  /**
   * OpenTelemetry deprecated {@code messaging.system} in favor of {@code messaging.system.name}.
   * Instrumentation may set either, so prefer {@code messaging.system} when both are set.
   */
  @SuppressWarnings("deprecation")
  static @Nullable String getMessagingSystem(final @NotNull Attributes attributes) {
    final @Nullable String messagingSystem =
        attributes.get(MessagingIncubatingAttributes.MESSAGING_SYSTEM);
    return messagingSystem != null
        ? messagingSystem
        : attributes.get(MessagingIncubatingAttributes.MESSAGING_SYSTEM_NAME);
  }

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
