package io.sentry.android.ndk;

import io.sentry.protocol.DebugImage;

/** Used only for making the Module list loader testable */
interface IModuleListLoader {
  DebugImage[] getModuleList();

  void clearModuleList();
}
