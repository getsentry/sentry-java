package io.sentry.android.core.replay;

import com.bytedance.shadowhook.ShadowHook;

public class RenderNodeTracing {
  static {
    ShadowHook.init(new ShadowHook.ConfigBuilder()
      .setMode(ShadowHook.Mode.SHARED)
      .build());
    System.loadLibrary("sentry-android-replay-headers");
  }

  private static native void nStartRenderNodeTracing();

  private static native void nStopRenderNodeTracing();

  public static void starTracing() {
    nStartRenderNodeTracing();
  }

  public static void stopTracing() {
    nStopRenderNodeTracing();
  }
}
