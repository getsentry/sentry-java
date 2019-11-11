package io.sentry.sample;

import java.lang.reflect.Method;

public class NativeSample {
  public static native void crash();

  static {
    System.loadLibrary("native-sample");
  }

  // TODO: Raise from native-sample.cpp instead
  public static void verificationEvent() {
    try {
      Class<?> cls = Class.forName("io.sentry.android.ndk.SentryNdk");
      Method method = cls.getMethod("verificationEvent");
      method.invoke(null);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
