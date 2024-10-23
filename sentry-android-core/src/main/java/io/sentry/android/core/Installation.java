package io.sentry.android.core;

import android.content.Context;
import io.sentry.ISentryLifecycleToken;
import io.sentry.SentryUUID;
import io.sentry.util.AutoClosableReentrantLock;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

final class Installation {
  @TestOnly static @Nullable String deviceId = null;

  @TestOnly static final String INSTALLATION = "INSTALLATION";

  private static final Charset UTF_8 = Charset.forName("UTF-8");

  protected static final @NotNull AutoClosableReentrantLock staticLock =
      new AutoClosableReentrantLock();

  private Installation() {}

  /**
   * Generates a random UUID and writes to a file to be used as an unique installationId. Reads the
   * installationId if already exists.
   *
   * @param context the Context
   * @return the generated installationId
   * @throws RuntimeException if not possible to read nor to write to the file.
   */
  public static String id(final @NotNull Context context) throws RuntimeException {
    try (final @NotNull ISentryLifecycleToken ignored = staticLock.acquire()) {
      if (deviceId == null) {
        final File installation = new File(context.getFilesDir(), INSTALLATION);
        try {
          if (!installation.exists()) {
            deviceId = writeInstallationFile(installation);
            return deviceId;
          }
          deviceId = readInstallationFile(installation);
        } catch (Throwable e) {
          throw new RuntimeException(e);
        }
      }
      return deviceId;
    }
  }

  @TestOnly
  static @NotNull String readInstallationFile(final @NotNull File installation) throws IOException {
    try (final RandomAccessFile f = new RandomAccessFile(installation, "r")) {
      final byte[] bytes = new byte[(int) f.length()];
      f.readFully(bytes);
      return new String(bytes, UTF_8);
    }
  }

  @TestOnly
  static @NotNull String writeInstallationFile(final @NotNull File installation)
      throws IOException {
    try (final OutputStream out = new FileOutputStream(installation)) {
      final String id = SentryUUID.generateSentryId();
      out.write(id.getBytes(UTF_8));
      out.flush();
      return id;
    }
  }
}
