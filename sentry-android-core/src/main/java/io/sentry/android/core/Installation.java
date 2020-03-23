package io.sentry.android.core;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

final class Installation {
  @TestOnly static String deviceId = null;

  @TestOnly static final String INSTALLATION = "INSTALLATION";
  private static final Charset UTF_8 = Charset.forName("UTF-8");

  private Installation() {}

  public static synchronized String id(final @NotNull Context context) throws RuntimeException {
    if (deviceId == null) {
      File installation = new File(context.getFilesDir(), INSTALLATION);
      try {
        if (!installation.exists()) {
          deviceId = writeInstallationFile(installation);
          return deviceId;
        }
        deviceId = readInstallationFile(installation);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    return deviceId;
  }

  @TestOnly
  static @NotNull String readInstallationFile(final @NotNull File installation) throws IOException {
    try (RandomAccessFile f = new RandomAccessFile(installation, "r")) {
      byte[] bytes = new byte[(int) f.length()];
      f.readFully(bytes);
      return new String(bytes, UTF_8);
    }
  }

  @TestOnly
  static @NotNull String writeInstallationFile(final @NotNull File installation)
      throws IOException {
    try (OutputStream out = new FileOutputStream(installation)) {
      String id = UUID.randomUUID().toString();
      out.write(id.getBytes(UTF_8));
      out.flush();
      return id;
    }
  }
}
