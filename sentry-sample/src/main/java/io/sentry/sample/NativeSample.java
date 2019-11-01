package io.sentry.sample;

public class NativeSample {
  public static native void crash();

  static {
    System.loadLibrary("native-sample");
  }
}
