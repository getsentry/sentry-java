package io.sentry.android.ndk;

import java.util.Arrays;
import java.util.List;

import io.sentry.protocol.DebugImage;

final class ModuleLoader implements IModuleLoader {
    @Override
    public List<DebugImage> getModuleList() {
        DebugImage[] objects = nativeGetModuleList();
        // objects is 288 size but all elements null
        if (objects != null) {
            return Arrays.asList(objects);
        }
        return null;
    }

    @Override
    public void clearModuleList() {
        nativeClearModuleList();
    }

    public static native DebugImage[] nativeGetModuleList();

    public static native void nativeClearModuleList();
}
