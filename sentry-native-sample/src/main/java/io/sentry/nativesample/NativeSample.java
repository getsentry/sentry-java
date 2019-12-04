package io.sentry.nativesample;

public class NativeSample {
  public static native void message();

  static {
    System.loadLibrary("native-sample");
  }
}
