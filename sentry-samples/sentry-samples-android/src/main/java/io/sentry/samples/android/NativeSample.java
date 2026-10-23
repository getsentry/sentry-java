package io.sentry.samples.android;

public class NativeSample {
  public static native void crash();

  public static native void message();

  // Named to demonstrate the value of native stack frames during ANR
  public static native void freezeMysteriously(Object obj);

  public static native void prepareCrash(String scenario, String runId);

  public static native void crashOutOfMemory();

  public static native void crashLockedHeap();

  public static native void crashCorruptedHeap();

  public static native void crashNoFileDescriptors();

  public static native void crashStackCorruption();

  public static native void crashStackOverflow();

  public static native void crashConcurrent();

  public static native void crashManyThreads();

  static {
    System.loadLibrary("native-sample");
  }
}
