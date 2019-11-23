package io.sentry.core;

import java.net.URI;

final class Dsn {
  private final String projectId;
  private final String path;
  private final String secretKey;
  private final String publicKey;
  private final URI sentryUri;

  /*
  / The project ID which the authenticated user is bound to.
  */
  public String getProjectId() {
    return projectId;
  }

  /*
  / An optional path of which Sentry is hosted
  */
  public String getPath() {
    return path;
  }

  /*
  / The optional secret key to authenticate the SDK.
  */
  public String getSecretKey() {
    return secretKey;
  }

  /*
  / The required public key to authenticate the SDK.
  */
  public String getPublicKey() {
    return publicKey;
  }

  /*
  / The URI used to communicate with Sentry
  */
  URI getSentryUri() {
    return sentryUri;
  }

  Dsn(String dsn) throws InvalidDsnException {
    try {
      URI uri = new URI(dsn);
      String userInfo = uri.getUserInfo();
      if (userInfo == null || userInfo.isEmpty()) {
        throw new IllegalArgumentException("Invalid DSN: No public key provided.");
      }
      String[] keys = userInfo.split(":", -1);
      publicKey = keys[0]; // TODO: test lack of delimiter returns whole value as first index
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
              path + "api/" + projectId + "/store/",
              null,
              null);
    } catch (Exception e) {
      throw new InvalidDsnException(dsn, e);
    }
  }
}
