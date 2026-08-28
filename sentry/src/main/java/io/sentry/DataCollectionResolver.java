package io.sentry;

import java.util.Set;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Resolves effective Data Collection policies for SDK integrations. */
@ApiStatus.Internal
public final class DataCollectionResolver {

  private static final @NotNull KeyValueCollectionBehavior OFF = KeyValueCollectionBehavior.off();
  private static final @NotNull KeyValueCollectionBehavior EMPTY_DENY_LIST =
      KeyValueCollectionBehavior.denyList();

  private final @NotNull SentryOptions options;

  DataCollectionResolver(final @NotNull SentryOptions options) {
    this.options = options;
  }

  public boolean isDataCollectionConfigured() {
    return options.getDataCollection().isExplicitlyConfigured();
  }

  public boolean isUserInfo() {
    return explicitOrSendDefaultPii(options.getDataCollection().getUserInfo(), true);
  }

  public boolean isDatabaseQueryData() {
    return explicitOrSendDefaultPii(options.getDataCollection().getDatabaseQueryData(), true);
  }

  public boolean isGraphqlDocument() {
    return explicitOrSendDefaultPii(options.getDataCollection().getGraphql().getDocument(), true);
  }

  public boolean isGraphqlDocumentWithLegacyBodyGate() {
    return explicitOrDefault(
        options.getDataCollection().getGraphql().getDocument(), true, isLegacyGraphqlBodyEnabled());
  }

  public boolean isGraphqlDocumentWithLegacyAlways() {
    return explicitOrDefault(options.getDataCollection().getGraphql().getDocument(), true, true);
  }

  public boolean isGraphqlVariables() {
    return explicitOrSendDefaultPii(options.getDataCollection().getGraphql().getVariables(), true);
  }

  public boolean isGraphqlVariablesWithLegacyBodyGate() {
    return explicitOrDefault(
        options.getDataCollection().getGraphql().getVariables(),
        true,
        isLegacyGraphqlBodyEnabled());
  }

  public boolean isGraphqlVariablesWithLegacyAlways() {
    return explicitOrDefault(options.getDataCollection().getGraphql().getVariables(), true, true);
  }

  public @NotNull KeyValueCollectionBehavior getCookies() {
    final @NotNull DataCollection dataCollection = options.getDataCollection();
    final @Nullable KeyValueCollectionBehavior cookies = dataCollection.getCookies();

    if (cookies != null) {
      return cookies;
    }
    if (isDataCollectionConfigured()) {
      return EMPTY_DENY_LIST;
    }
    return options.isSendDefaultPii() ? EMPTY_DENY_LIST : OFF;
  }

  public @NotNull KeyValueCollectionBehavior getUrlQueryParams() {
    return explicitOrEmptyDenyList(options.getDataCollection().getUrlQueryParams());
  }

  public @NotNull KeyValueCollectionBehavior getHttpRequestHeaders() {
    return explicitOrEmptyDenyList(options.getDataCollection().getHttpHeaders().getRequest());
  }

  public @NotNull KeyValueCollectionBehavior getHttpResponseHeaders() {
    return explicitOrEmptyDenyList(options.getDataCollection().getHttpHeaders().getResponse());
  }

  public boolean isIncomingRequestBody() {
    return isHttpBodyEnabled(HttpBodyType.INCOMING_REQUEST, options.isSendDefaultPii());
  }

  public boolean isOutgoingRequestBody() {
    return isHttpBodyEnabled(HttpBodyType.OUTGOING_REQUEST, true);
  }

  public boolean isIncomingResponseBody() {
    return isHttpBodyEnabled(HttpBodyType.INCOMING_RESPONSE, true);
  }

  public boolean isOutgoingResponseBody() {
    return isHttpBodyEnabled(HttpBodyType.OUTGOING_RESPONSE, options.isSendDefaultPii());
  }

  private boolean isLegacyGraphqlBodyEnabled() {
    return options.isSendDefaultPii()
        && !SentryOptions.RequestSize.NONE.equals(options.getMaxRequestBodySize());
  }

  private boolean explicitOrSendDefaultPii(
      final @Nullable Boolean explicit, final boolean defaultValue) {
    return explicitOrDefault(explicit, defaultValue, options.isSendDefaultPii());
  }

  private boolean explicitOrDefault(
      final @Nullable Boolean explicit, final boolean defaultValue, final boolean legacyFallback) {
    if (explicit != null) {
      return explicit;
    }
    return isDataCollectionConfigured() ? defaultValue : legacyFallback;
  }

  private @NotNull KeyValueCollectionBehavior explicitOrEmptyDenyList(
      final @Nullable KeyValueCollectionBehavior explicit) {
    return explicit != null ? explicit : EMPTY_DENY_LIST;
  }

  private boolean isHttpBodyEnabled(
      final @NotNull HttpBodyType bodyType, final boolean legacyFallback) {
    final @NotNull DataCollection dataCollection = options.getDataCollection();
    final @Nullable Set<HttpBodyType> httpBodies = dataCollection.getHttpBodies();
    if (httpBodies != null) {
      return httpBodies.contains(bodyType);
    }
    return isDataCollectionConfigured() || legacyFallback;
  }
}
