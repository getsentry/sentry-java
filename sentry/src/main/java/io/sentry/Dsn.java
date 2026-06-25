package io.sentry;

import io.sentry.util.Objects;
import java.net.URI;
import java.net.URISyntaxException;
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

  /** The project ID which the authenticated user is bound to. */
  public @NotNull String getProjectId() {
    return projectId;
  }

  /** An optional path of which Sentry is hosted. */
  public @Nullable String getPath() {
    return path;
  }

  /** The optional secret key to authenticate the SDK. */
  public @Nullable String getSecretKey() {
    return secretKey;
  }

  /** The required public key to authenticate the SDK. */
  public @NotNull String getPublicKey() {
    return publicKey;
  }

  /** The org ID extracted from the host, or {@code null} when the host has no org prefix. */
  public @Nullable String getOrgId() {
    return orgId;
  }

  /** The URI used to communicate with Sentry. */
  @NotNull
  URI getSentryUri() {
    return sentryUri;
  }

  // Avoids java.net.URI for DSN parsing, which is slow on Android.
  Dsn(@Nullable String dsn) throws IllegalArgumentException {
    final String dsnString = Objects.requireNonNull(dsn, "The DSN is required.").trim();
    if (dsnString.isEmpty()) {
      throw new IllegalArgumentException("The DSN is empty.");
    }

    try {
      final int schemeEnd = dsnString.indexOf("://");
      if (schemeEnd < 0) {
        throw new IllegalArgumentException("Invalid DSN: Missing scheme.");
      }
      final String scheme = dsnString.substring(0, schemeEnd);
      if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
        throw new IllegalArgumentException("Invalid DSN: Invalid scheme '" + scheme + "'.");
      }

      final int authStart = schemeEnd + 3;
      final int atIndex = dsnString.indexOf('@', authStart);
      if (atIndex < 0) {
        throw new IllegalArgumentException("Invalid DSN: No public key provided.");
      }
      final String userInfo = dsnString.substring(authStart, atIndex);
      final int colonIndex = userInfo.indexOf(':');
      publicKey = colonIndex < 0 ? userInfo : userInfo.substring(0, colonIndex);
      secretKey = colonIndex < 0 ? null : userInfo.substring(colonIndex + 1);
      if (publicKey.isEmpty()) {
        throw new IllegalArgumentException("Invalid DSN: No public key provided.");
      }

      final String hostAndPath = stripQueryAndFragment(dsnString, atIndex + 1);
      final int firstSlash = hostAndPath.indexOf('/');
      if (firstSlash < 0) {
        throw new IllegalArgumentException("Invalid DSN: A Project Id is required.");
      }

      final String hostPort = hostAndPath.substring(0, firstSlash);
      final int portColon = portSeparatorIndex(hostPort);
      final String host = portColon < 0 ? hostPort : hostPort.substring(0, portColon);
      final int port = portColon < 0 ? -1 : parsePort(hostPort.substring(portColon + 1));

      final String rawPath = stripTrailingSlash(collapseSlashes(hostAndPath.substring(firstSlash)));
      final int projectIdStart = rawPath.lastIndexOf('/') + 1;
      path = ensureTrailingSlash(rawPath.substring(0, projectIdStart));
      projectId = rawPath.substring(projectIdStart);
      if (projectId.isEmpty()) {
        throw new IllegalArgumentException("Invalid DSN: A Project Id is required.");
      }

      sentryUri = new URI(scheme, null, host, port, path + "api/" + projectId, null, null);
      orgId = extractOrgId(host);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("Invalid DSN: " + e.getMessage(), e);
    }
  }

  private static int parsePort(final @NotNull String portString) {
    try {
      return Integer.parseInt(portString);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid DSN: Invalid port '" + portString + "'.", e);
    }
  }

  // Drops the query string and/or fragment, whichever appears first, from the host onwards.
  private static @NotNull String stripQueryAndFragment(
      final @NotNull String dsn, final int fromIndex) {
    int cut = dsn.indexOf('?', fromIndex);
    final int fragment = dsn.indexOf('#', fromIndex);
    if (fragment >= 0 && (cut < 0 || fragment < cut)) {
      cut = fragment;
    }
    return cut < 0 ? dsn.substring(fromIndex) : dsn.substring(fromIndex, cut);
  }

  // IPv6 literals are bracketed and contain colons, so the port separator follows the ']'.
  private static int portSeparatorIndex(final @NotNull String hostPort) {
    return hostPort.startsWith("[")
        ? hostPort.indexOf(':', hostPort.indexOf(']'))
        : hostPort.indexOf(':');
  }

  // Collapses runs of slashes into a single slash, like URI.normalize().
  private static @NotNull String collapseSlashes(final @NotNull String path) {
    if (!path.contains("//")) {
      return path;
    }
    final StringBuilder sb = new StringBuilder(path.length());
    char previous = 0;
    for (int i = 0; i < path.length(); i++) {
      final char c = path.charAt(i);
      if (c == '/' && previous == '/') {
        continue;
      }
      sb.append(c);
      previous = c;
    }
    return sb.toString();
  }

  private static @NotNull String stripTrailingSlash(final @NotNull String path) {
    return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
  }

  private static @NotNull String ensureTrailingSlash(final @NotNull String path) {
    return path.endsWith("/") ? path : path + "/";
  }

  // Extracts the org ID from a host such as "o123.ingest.sentry.io" -> "123".
  private static @Nullable String extractOrgId(final @NotNull String host) {
    final Matcher matcher = ORG_ID_PATTERN.matcher(host);
    return matcher.find() ? matcher.group(1) : null;
  }
}
