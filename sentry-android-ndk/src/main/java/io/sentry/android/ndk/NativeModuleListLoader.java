package io.sentry.android.ndk;

import io.sentry.protocol.DebugImage;
import org.jetbrains.annotations.Nullable;

class NativeModuleListLoader implements IModuleListLoader {
  @Override
  public @Nullable DebugImage[] getModuleList() {
    return nativeGetModuleList();
  }

  @Override
  public void clearModuleList() {
    nativeClearModuleList();
  }

  public static native DebugImage[] nativeGetModuleList();

  public static native void nativeClearModuleList();
}
