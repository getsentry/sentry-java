package android.graphics;

import android.util.Log;

public class RenderNodeHelper {
  static {
    System.loadLibrary("sentry-android-replay");
    System.loadLibrary("sentry-android-replay-headers");
  }

  public static native void nGetDisplayList(long renderNode);

  public static native void nGetDisplayList2(long renderNode);

  public static void fetchDisplayList(long renderNode) {
    nGetDisplayList2(renderNode);
    Log.e("TEST", "TEST");
  }
}
