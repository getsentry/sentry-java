package io.sentry.android.ndk;

import java.util.List;

import io.sentry.protocol.DebugImage;

interface IModuleLoader {
    List<DebugImage> getModuleList();

    void clearModuleList();
}
