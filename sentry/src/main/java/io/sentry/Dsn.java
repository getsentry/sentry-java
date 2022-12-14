package io.sentry;

import io.sentry.util.Objects;
import java.net.URI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class Dsn {
  private final @NotNull String projectId;
  private final @Nullable String path;
  private final @Nullable String secretKey;
  private final @NotNull String publicKey;
  private final @NotNull URI sentryUri;

  /*
  / The project ID which the authenticated user is bound to.
  */
  public @NotNull String getProjectId() {
    return projectId;
  }

  /*
  / An optional path of which Sentry is hosted
  */
  public @Nullable String getPath() {
    return path;
  }

  /*
  / The optional secret key to authenticate the SDK.
  */
  public @Nullable String getSecretKey() {
    return secretKey;
  }

  /*
  / The required public key to authenticate the SDK.
  */
  public @NotNull String getPublicKey() {
    return publicKey;
  }

  /*
  / The URI used to communicate with Sentry
  */
  @NotNull
  URI getSentryUri() {
    return sentryUri;
  }

  Dsn(@Nullable String dsn) throws IllegalArgumentException {
    try {
      Objects.requireNonNull(dsn, "The DSN is required.");
      URI uri = new URI(dsn).normalize();
      String userInfo = uri.getUserInfo();
      if (userInfo == null || userInfo.isEmpty()) {
        throw new IllegalArgumentException("Invalid DSN: No public key provided.");
      }
      String[] keys = userInfo.split(":", -1);
      publicKey = keys[0];
      if (publicKey == null || publicKey.isEmpty()) {
        throw new IllegalArgumentException("Invalid DSN: No public key provided.");
      }
      secretKey = keys.length > 1 ? keys[1] : null;
      String uriPath = uri.getPath();
      if (uriPath.endsWith("/")) {
        uriPath = uriPath.substring(0, uriPath.length() - 1);
      }
      int projectIdStart = uriPath.lastIndexOf("/") + 1;
      String path = uriPath.substring(0, projectIdStart);
      if (!path.endsWith("/")) {
        path += "/";
      }
      this.path = path;
      projectId = uriPath.substring(projectIdStart);
      if (projectId.isEmpty()) {
        throw new IllegalArgumentException("Invalid DSN: A Project Id is required.");
      }
      sentryUri =
          new URI(
              uri.getScheme(),
              null,
              uri.getHost(),
              uri.getPort(),
              path + "api/" + projectId,
              null,
              null);
    } catch (Throwable e) {
      throw new IllegalArgumentException(e);
    }
  }
}
