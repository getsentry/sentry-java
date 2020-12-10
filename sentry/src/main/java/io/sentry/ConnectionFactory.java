package io.sentry;

import io.sentry.transport.Connection;
import org.jetbrains.annotations.NotNull;

public final class ConnectionFactory {
  private ConnectionFactory() {}

  public static @NotNull Connection create(final @NotNull SentryOptions options) {
    if (classExists("org.asynchttpclient.AsyncHttpClient")) {
      return NIOConnectionFactory.create(options);
    } else {
      return AsyncConnectionFactory.create(options, options.getEnvelopeDiskCache());
    }
  }

  private static boolean classExists(String clazz) {
    try {
      Class.forName(clazz, false, ClassLoader.getSystemClassLoader());
      return true;
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
      return false;
    }
  }
}
