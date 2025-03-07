package io.sentry.opentelemetry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.sentry.DsnUtil;
import io.sentry.IScopes;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class OtelInternalSpanDetectionUtil {

  private static final @NotNull List<SpanKind> spanKindsConsideredForSentryRequests =
      Arrays.asList(SpanKind.CLIENT, SpanKind.INTERNAL);
  private static final @NotNull OpenTelemetryAttributesExtractor attributesExtractor =
      new OpenTelemetryAttributesExtractor();

  @SuppressWarnings("deprecation")
  public static boolean isSentryRequest(
      final @NotNull IScopes scopes,
      final @NotNull SpanKind spanKind,
      final @NotNull Attributes attributes) {
    if (!spanKindsConsideredForSentryRequests.contains(spanKind)) {
      return false;
    }

    String url = attributesExtractor.extractUrl(attributes, scopes.getOptions());
    if (DsnUtil.urlContainsDsnHost(scopes.getOptions(), url)) {
      return true;
    }

    if (scopes.getOptions().isEnableSpotlight()) {
      final @Nullable String optionsSpotlightUrl = scopes.getOptions().getSpotlightConnectionUrl();
      final @NotNull String spotlightUrl =
          optionsSpotlightUrl != null ? optionsSpotlightUrl : "http://localhost:8969/stream";

      if (containsSpotlightUrl(url, spotlightUrl)) {
        return true;
      }
    }

    return false;
  }

  private static boolean containsSpotlightUrl(
      final @Nullable String requestUrl, final @NotNull String spotlightUrl) {
    if (requestUrl == null) {
      return false;
    }

    return requestUrl.toLowerCase(Locale.ROOT).contains(spotlightUrl.toLowerCase(Locale.ROOT));
  }
}
