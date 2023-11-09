package android.graphics;

import android.util.Log;

public class RenderNodeHelper {
  static {
    System.loadLibrary("sentry-android-replay");
  }

  public static native void nGetDisplayList(long renderNode);

  public static void fetchDisplayList(long renderNode) {
    nGetDisplayList(renderNode);
    Log.e("TEST", "TEST");
  }
}
