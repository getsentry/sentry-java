package io.sentry.android.ndk;

import java.util.List;

final class ModuleLoader implements IModuleLoader {
    @Override
    public List<Object> getModuleList() {
        return nativeGetModuleList();
    }

    @Override
    public void clearModuleList() {
        nativeClearModuleList();
    }

    public static native List<Object> nativeGetModuleList();

    public static native void nativeClearModuleList();
}
