package io.sentry.opentelemetry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.semconv.UrlAttributes;
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

  @SuppressWarnings("deprecation")
  public static boolean isSentryRequest(
      final @NotNull IScopes scopes,
      final @NotNull SpanKind spanKind,
      final @NotNull Attributes attributes) {
    if (!spanKindsConsideredForSentryRequests.contains(spanKind)) {
      return false;
    }

    final @Nullable String httpUrl =
        attributes.get(io.opentelemetry.semconv.SemanticAttributes.HTTP_URL);
    if (DsnUtil.urlContainsDsnHost(scopes.getOptions(), httpUrl)) {
      return true;
    }

    final @Nullable String fullUrl = attributes.get(UrlAttributes.URL_FULL);
    if (DsnUtil.urlContainsDsnHost(scopes.getOptions(), fullUrl)) {
      return true;
    }

    if (scopes.getOptions().isEnableSpotlight()) {
      final @Nullable String optionsSpotlightUrl = scopes.getOptions().getSpotlightConnectionUrl();
      final @NotNull String spotlightUrl =
          optionsSpotlightUrl != null ? optionsSpotlightUrl : "http://localhost:8969/stream";

      if (containsSpotlightUrl(fullUrl, spotlightUrl)) {
        return true;
      }
      if (containsSpotlightUrl(httpUrl, spotlightUrl)) {
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
