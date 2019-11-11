package io.sentry.android.ndk;

import io.sentry.core.SentryOptions;

public class SentryNdk {
  static {
    System.loadLibrary("sentry");
  }

  static {
    System.loadLibrary("sentry-android");
  }

  private static native void initSentryNative(SentryOptions options);

  private static native void verificationEventNative();

  public static void init(SentryOptions options) {
    initSentryNative(options);
  }

  public static void verificationEvent() {
    verificationEventNative();
  }
}
