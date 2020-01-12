package io.sentry.android.core;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.util.UUID;
import org.jetbrains.annotations.TestOnly;

final class Installation {
  @TestOnly static String deviceId = null;

  private static final String INSTALLATION = "INSTALLATION";
  private static final Charset UTF_8 = Charset.forName("UTF-8");

  private Installation() {}

  public static synchronized String id(Context context) {
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
  static String readInstallationFile(File installation) throws IOException {
    RandomAccessFile f = new RandomAccessFile(installation, "r");
    byte[] bytes = new byte[(int) f.length()];
    f.readFully(bytes);
    f.close();
    return new String(bytes, UTF_8);
  }

  @TestOnly
  static String writeInstallationFile(File installation) throws IOException {
    FileOutputStream out = new FileOutputStream(installation);
    String id = UUID.randomUUID().toString();
    out.write(id.getBytes(UTF_8));
    out.close();
    return id;
  }
}
