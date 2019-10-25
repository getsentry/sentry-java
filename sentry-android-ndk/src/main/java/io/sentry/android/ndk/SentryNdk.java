package io.sentry.android.ndk;

import io.sentry.core.SentryOptions;

public class SentryNdk {
  static {
    System.loadLibrary("sentry");
  }

  static {
    System.loadLibrary("sentry-android");
  }

  private static native void initSentryNative(String cacheDirPath);

  public static void notifyNewSerializedEnvelope(String path) {
    System.out.println("envelope written to " + path);
  }

  public static void init(SentryOptions options) {
    // Java_example
    initSentryNative(options.getCacheDirPath());
  }
}
