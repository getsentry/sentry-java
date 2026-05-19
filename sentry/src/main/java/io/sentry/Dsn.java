package io.sentry;

import io.sentry.util.Objects;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class Dsn {
  private static final @NotNull Pattern ORG_ID_PATTERN = Pattern.compile("^o(\\d+)\\.");

  private final @NotNull String projectId;
  private final @Nullable String path;
  private final @Nullable String secretKey;
  private final @NotNull String publicKey;
  private final @NotNull URI sentryUri;
  private final @Nullable String orgId;

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

  // Avoids java.net.URI for DSN parsing, which is slow on Android.
  Dsn(@Nullable String dsn) throws IllegalArgumentException {
    try {
      final String dsnString = Objects.requireNonNull(dsn, "The DSN is required.").trim();
      if (dsnString.isEmpty()) {
        throw new IllegalArgumentException("The DSN is empty.");
      }

      // Extract scheme
      final int schemeEnd = dsnString.indexOf("://");
      if (schemeEnd < 0) {
        throw new IllegalArgumentException("Invalid DSN: missing scheme.");
      }
      final String scheme = dsnString.substring(0, schemeEnd);
      if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
        throw new IllegalArgumentException("Invalid DSN scheme: " + scheme);
      }

      // Extract userinfo (public key and optional secret key)
      final int authStart = schemeEnd + 3;
      final int atIndex = dsnString.indexOf('@', authStart);
      if (atIndex < 0) {
        throw new IllegalArgumentException("Invalid DSN: No public key provided.");
      }
      final String userInfo = dsnString.substring(authStart, atIndex);
      if (userInfo.isEmpty()) {
        throw new IllegalArgumentException("Invalid DSN: No public key provided.");
      }
      final int colonIndex = userInfo.indexOf(':');
      if (colonIndex < 0) {
        publicKey = userInfo;
        secretKey = null;
      } else {
        publicKey = userInfo.substring(0, colonIndex);
        secretKey = userInfo.substring(colonIndex + 1);
      }
      if (publicKey.isEmpty()) {
        throw new IllegalArgumentException("Invalid DSN: No public key provided.");
      }

      // Extract host, optional port, and path+projectId
      final int hostStart = atIndex + 1;

      // Strip query string if present
      final int queryIndex = dsnString.indexOf('?', hostStart);
      final String hostAndPath =
          queryIndex < 0
              ? dsnString.substring(hostStart)
              : dsnString.substring(hostStart, queryIndex);

      final int firstSlash = hostAndPath.indexOf('/');
      if (firstSlash < 0) {
        throw new IllegalArgumentException("Invalid DSN: A Project Id is required.");
      }

      final String hostPort = hostAndPath.substring(0, firstSlash);
      final int portColon = hostPort.indexOf(':');
      final String host;
      final int port;
      if (portColon < 0) {
        host = hostPort;
        port = -1;
      } else {
        host = hostPort.substring(0, portColon);
        port = Integer.parseInt(hostPort.substring(portColon + 1));
      }

      // Normalize the path (collapse double slashes, like URI.normalize())
      String rawPath = hostAndPath.substring(firstSlash);
      while (rawPath.contains("//")) {
        rawPath = rawPath.replace("//", "/");
      }

      if (rawPath.endsWith("/")) {
        rawPath = rawPath.substring(0, rawPath.length() - 1);
      }
      final int projectIdStart = rawPath.lastIndexOf('/') + 1;
      String pathSegment = rawPath.substring(0, projectIdStart);
      if (!pathSegment.endsWith("/")) {
        pathSegment += "/";
      }
      this.path = pathSegment;
      projectId = rawPath.substring(projectIdStart);
      if (projectId.isEmpty()) {
        throw new IllegalArgumentException("Invalid DSN: A Project Id is required.");
      }

      sentryUri = new URI(scheme, null, host, port, pathSegment + "api/" + projectId, null, null);

      // Extract org ID from host (e.g., "o123.ingest.sentry.io" -> "123")
      String extractedOrgId = null;
      final Matcher matcher = ORG_ID_PATTERN.matcher(host);
      if (matcher.find()) {
        extractedOrgId = matcher.group(1);
      }
      orgId = extractedOrgId;
    } catch (Throwable e) {
      throw new IllegalArgumentException(e);
    }
  }

  public @Nullable String getOrgId() {
    return orgId;
  }
}
