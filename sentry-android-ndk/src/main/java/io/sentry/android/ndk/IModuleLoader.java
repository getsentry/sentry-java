package io.sentry.android.ndk;

import java.util.List;

interface IModuleLoader {
    List<Object> getModuleList();

    void clearModuleList();
}
