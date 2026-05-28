package io.sentry.jdbc;

import com.p6spy.engine.common.ConnectionInformation;
import com.p6spy.engine.common.StatementInformation;
import io.sentry.util.StringUtils;
import java.net.URI;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DatabaseUtils {

  private static final @NotNull DatabaseDetails EMPTY = new DatabaseDetails(null, null);

  public static DatabaseDetails readFrom(
      final @Nullable StatementInformation statementInformation) {
    if (statementInformation == null) {
      return EMPTY;
    }

    final @Nullable ConnectionInformation connectionInformation =
        statementInformation.getConnectionInformation();
    return readFrom(connectionInformation);
  }

  public static DatabaseDetails readFrom(
      final @Nullable ConnectionInformation connectionInformation) {
    if (connectionInformation == null) {
      return EMPTY;
    }

    return parse(connectionInformation.getUrl());
  }

  public static DatabaseDetails parse(final @Nullable String databaseConnectionUrl) {
    if (databaseConnectionUrl == null) {
      return EMPTY;
    }
    try {
      final @NotNull String rawUrl =
          removeP6SpyPrefix(databaseConnectionUrl.toLowerCase(Locale.ROOT));
      final @NotNull String[] urlParts = rawUrl.split(":", -1);
      if (urlParts.length > 1) {
        final @NotNull String dbSystem = urlParts[0];
        return parseDbSystemSpecific(dbSystem, urlParts, rawUrl);
      }
    } catch (Throwable t) {
      // ignore
    }

    return EMPTY;
  }

  private static @NotNull DatabaseDetails parseDbSystemSpecific(
      final @NotNull String dbSystem,
      final @NotNull String[] urlParts,
      final @NotNull String urlString) {
    if ("hsqldb".equalsIgnoreCase(dbSystem)
        || "h2".equalsIgnoreCase(dbSystem)
        || "derby".equalsIgnoreCase(dbSystem)
        || "sqlite".equalsIgnoreCase(dbSystem)) {
      if (urlString.contains("//")) {
        return parseAsUri(dbSystem, StringUtils.removePrefix(urlString, dbSystem + ":"));
      }
      if (urlParts.length > 2) {
        String dbNameAndMaybeMore = urlParts[2];
        return new DatabaseDetails(dbSystem, StringUtils.substringBefore(dbNameAndMaybeMore, ";"));
      }
      if (urlParts.length > 1) {
        String dbNameAndMaybeMore = urlParts[1];
        return new DatabaseDetails(dbSystem, StringUtils.substringBefore(dbNameAndMaybeMore, ";"));
      }
    }
    if ("mariadb".equalsIgnoreCase(dbSystem)
        || "mysql".equalsIgnoreCase(dbSystem)
        || "postgresql".equalsIgnoreCase(dbSystem)
        || "mongo".equalsIgnoreCase(dbSystem)) {
      return parseAsUri(dbSystem, urlString);
    }
    if ("sqlserver".equalsIgnoreCase(dbSystem)) {
      try {
        String dbProperty = ";databasename=";
        final int index = urlString.indexOf(dbProperty);
        if (index >= 0) {
          final @NotNull String dbNameAndMaybeMore =
              urlString.substring(index + dbProperty.length());
          return new DatabaseDetails(
              dbSystem, StringUtils.substringBefore(dbNameAndMaybeMore, ";"));
        }
      } catch (Throwable t) {
        // ignore
      }
    }
    if ("oracle".equalsIgnoreCase(dbSystem)) {
      String uriPrefix = "oracle:thin:@//";
      final int indexOfUri = urlString.indexOf(uriPrefix);
      if (indexOfUri >= 0) {
        final @NotNull String uri =
            "makethisaprotocol://" + urlString.substring(indexOfUri + uriPrefix.length());
        return parseAsUri(dbSystem, uri);
      }

      final int indexOfTnsNames = urlString.indexOf("oracle:thin:@(");
      if (indexOfTnsNames >= 0) {
        String serviceNamePrefix = "(service_name=";
        final int indexOfServiceName = urlString.indexOf(serviceNamePrefix);
        if (indexOfServiceName >= 0) {
          final int indexOfClosingBrace = urlString.indexOf(")", indexOfServiceName);
          final @NotNull String serviceName =
              urlString.substring(
                  indexOfServiceName + serviceNamePrefix.length(), indexOfClosingBrace);
          return new DatabaseDetails(dbSystem, serviceName);
        }
      }
    }
    if ("datadirect".equalsIgnoreCase(dbSystem)
        || "tibcosoftware".equalsIgnoreCase(dbSystem)
        || "jtds".equalsIgnoreCase(dbSystem)
        || "microsoft".equalsIgnoreCase(dbSystem)) {
      return parse(StringUtils.removePrefix(urlString, dbSystem + ":"));
    }

    return new DatabaseDetails(dbSystem, null);
  }

  private static @NotNull DatabaseDetails parseAsUri(
      final @NotNull String dbSystem, final @NotNull String urlString) {
    try {
      final @NotNull URI url = new URI(urlString);
      String path = StringUtils.removePrefix(url.getPath(), "/");
      String pathWithoutProperties = StringUtils.substringBefore(path, ";");
      return new DatabaseDetails(dbSystem, pathWithoutProperties);
    } catch (Throwable t) {
      System.out.println(t.getMessage());
      // ignore
    }
    return new DatabaseDetails(dbSystem, null);
  }

  private static @NotNull String removeP6SpyPrefix(final @NotNull String url) {
    return StringUtils.removePrefix(StringUtils.removePrefix(url, "jdbc:"), "p6spy:");
  }

  public static final class DatabaseDetails {
    private final @Nullable String dbSystem;
    private final @Nullable String dbName;

    DatabaseDetails(final @Nullable String dbSystem, final @Nullable String dbName) {
      this.dbSystem = dbSystem;
      this.dbName = dbName;
    }

    public @Nullable String getDbSystem() {
      return dbSystem;
    }

    public @Nullable String getDbName() {
      return dbName;
    }
  }
}
