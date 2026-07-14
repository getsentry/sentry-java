package io.sentry;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Configures data that the SDK collects automatically. */
public final class DataCollection {

  private boolean overridden;
  private @Nullable Boolean userInfo;
  private @Nullable KeyValueCollectionBehavior cookies;
  private @Nullable KeyValueCollectionBehavior queryParams;
  private @Nullable Set<HttpBodyType> httpBodies;
  private @Nullable Boolean databaseQueryData;
  private @Nullable Boolean queues;
  private final @NotNull HttpHeaders httpHeaders = new HttpHeaders();
  private final @NotNull Graphql graphql = new Graphql();

  public DataCollection() {
    this(true);
  }

  DataCollection(final boolean overridden) {
    this.overridden = overridden;
  }

  public @Nullable Boolean getUserInfo() {
    return userInfo;
  }

  public void setUserInfo(final boolean userInfo) {
    this.userInfo = userInfo;
  }

  public @Nullable KeyValueCollectionBehavior getCookies() {
    return cookies;
  }

  public void setCookies(final @Nullable KeyValueCollectionBehavior cookies) {
    this.cookies = cookies;
  }

  public @Nullable KeyValueCollectionBehavior getQueryParams() {
    return queryParams;
  }

  public void setQueryParams(final @Nullable KeyValueCollectionBehavior queryParams) {
    this.queryParams = queryParams;
  }

  public @Nullable Set<HttpBodyType> getHttpBodies() {
    return httpBodies;
  }

  public void setHttpBodies(final @Nullable Set<HttpBodyType> httpBodies) {
    this.httpBodies =
        httpBodies == null
            ? null
            : httpBodies.isEmpty()
                ? Collections.<HttpBodyType>emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(httpBodies));
  }

  public @Nullable Boolean getDatabaseQueryData() {
    return databaseQueryData;
  }

  public void setDatabaseQueryData(final boolean databaseQueryData) {
    this.databaseQueryData = databaseQueryData;
  }

  public @Nullable Boolean getQueues() {
    return queues;
  }

  public void setQueues(final boolean queues) {
    this.queues = queues;
  }

  public @NotNull HttpHeaders getHttpHeaders() {
    return httpHeaders;
  }

  public @NotNull Graphql getGraphql() {
    return graphql;
  }

  @ApiStatus.Internal
  boolean isExplicitlyConfigured() {
    return overridden
        || userInfo != null
        || cookies != null
        || queryParams != null
        || httpBodies != null
        || databaseQueryData != null
        || queues != null
        || httpHeaders.hasOverrides()
        || graphql.hasOverrides();
  }

  /** Configures collection of request and response HTTP headers. */
  public static final class HttpHeaders {
    private @Nullable KeyValueCollectionBehavior request;
    private @Nullable KeyValueCollectionBehavior response;

    public @Nullable KeyValueCollectionBehavior getRequest() {
      return request;
    }

    public void setRequest(final @Nullable KeyValueCollectionBehavior request) {
      this.request = request;
    }

    public @Nullable KeyValueCollectionBehavior getResponse() {
      return response;
    }

    public void setResponse(final @Nullable KeyValueCollectionBehavior response) {
      this.response = response;
    }

    private boolean hasOverrides() {
      return request != null || response != null;
    }
  }

  /** Configures collection of GraphQL document and variable content. */
  public static final class Graphql {
    private @Nullable Boolean document;
    private @Nullable Boolean variables;

    public @Nullable Boolean getDocument() {
      return document;
    }

    public void setDocument(final boolean document) {
      this.document = document;
    }

    public @Nullable Boolean getVariables() {
      return variables;
    }

    public void setVariables(final boolean variables) {
      this.variables = variables;
    }

    private boolean hasOverrides() {
      return document != null || variables != null;
    }
  }
}
