package io.sentry.android.ndk;

import io.sentry.protocol.DebugImage;
import org.jetbrains.annotations.Nullable;

class NativeModuleListLoader implements IModuleListLoader {
  @Override
  public @Nullable DebugImage[] loadModuleList() {
    return nativeLoadModuleList();
  }

  @Override
  public void clearModuleList() {
    nativeClearModuleList();
  }

  public static native DebugImage[] nativeLoadModuleList();

  public static native void nativeClearModuleList();
}
