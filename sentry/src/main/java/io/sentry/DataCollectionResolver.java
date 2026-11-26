package io.sentry;

import java.util.Set;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Resolves effective Data Collection policies for SDK integrations. */
@ApiStatus.Internal
public final class DataCollectionResolver {

  private final @NotNull SentryOptions options;

  DataCollectionResolver(final @NotNull SentryOptions options) {
    this.options = options;
  }

  public boolean isUserInfo() {
    return explicitOrDefault(options.getDataCollection().getUserInfo(), true);
  }

  public boolean isDatabaseQueryData() {
    return explicitOrDefault(options.getDataCollection().getDatabaseQueryData(), true);
  }

  public boolean isFilePaths() {
    return explicitOrDefault(options.getDataCollection().getFilePaths(), true);
  }

  public boolean isGraphqlDocument() {
    return explicitOrDefault(options.getDataCollection().getGraphql().getDocument(), true);
  }

  public boolean isGraphqlVariables() {
    return explicitOrDefault(options.getDataCollection().getGraphql().getVariables(), true);
  }

  public @NotNull KeyValueCollectionBehavior getCookies() {
    final @Nullable KeyValueCollectionBehavior cookies = options.getDataCollection().getCookies();
    return cookies != null ? cookies : KeyValueCollectionBehavior.denyList();
  }

  public @NotNull KeyValueCollectionBehavior getUrlQueryParams() {
    return explicitOrDefault(options.getDataCollection().getUrlQueryParams());
  }

  public @NotNull KeyValueCollectionBehavior getHttpRequestHeaders() {
    return explicitOrDefault(options.getDataCollection().getHttpHeaders().getRequest());
  }

  public @NotNull KeyValueCollectionBehavior getHttpResponseHeaders() {
    return explicitOrDefault(options.getDataCollection().getHttpHeaders().getResponse());
  }

  public boolean isIncomingRequestBody() {
    return isHttpBodyEnabled(HttpBodyType.INCOMING_REQUEST);
  }

  public boolean isOutgoingRequestBody() {
    return isHttpBodyEnabled(HttpBodyType.OUTGOING_REQUEST);
  }

  public boolean isIncomingResponseBody() {
    return isHttpBodyEnabled(HttpBodyType.INCOMING_RESPONSE);
  }

  public boolean isOutgoingResponseBody() {
    return isHttpBodyEnabled(HttpBodyType.OUTGOING_RESPONSE);
  }

  private boolean explicitOrDefault(final @Nullable Boolean explicit, final boolean defaultValue) {
    return explicit != null ? explicit : defaultValue;
  }

  private @NotNull KeyValueCollectionBehavior explicitOrDefault(
      final @Nullable KeyValueCollectionBehavior explicit) {
    return explicit != null ? explicit : KeyValueCollectionBehavior.denyList();
  }

  private boolean isHttpBodyEnabled(final @NotNull HttpBodyType bodyType) {
    final @Nullable Set<HttpBodyType> httpBodies = options.getDataCollection().getHttpBodies();
    return httpBodies == null || httpBodies.contains(bodyType);
  }
}
