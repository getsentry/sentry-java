package io.sentry.samples.android;

public class NativeSample {
  public static native void crash();

  public static native void message();

  // Named to demonstrate the value of native stack frames during ANR
  public static native void freezeMysteriously(Object obj);

  static {
    System.loadLibrary("native-sample");
  }
}
