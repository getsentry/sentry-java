package io.sentry.android.ndk;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.android.core.IDebugImagesLoader;
import io.sentry.protocol.DebugImage;

final class DebugImagesLoader implements IDebugImagesLoader {

    private final @NotNull SentryOptions options;
    private final @NotNull IModuleLoader moduleLoader;

    private static @Nullable List<DebugImage> debugImages;

    DebugImagesLoader(final @NotNull SentryOptions options, final @NotNull IModuleLoader moduleLoader) {
        this.options = options;
        this.moduleLoader = moduleLoader;
    }

    @Override
    public List<DebugImage> getDebugImages() {
        if (!options.isEnableNdk()) {
            // TODO: or throw? lets see
            return null;
        }

        // TODO: synchronize operations as it is a static field
        if (debugImages != null) {
            return debugImages;
        }

        try {
            List<DebugImage> list = moduleLoader.getModuleList();
            options.getLogger().log(SentryLevel.DEBUG, "list size %d", list.size());
        } catch (Exception e) {
            options.getLogger().log(SentryLevel.ERROR, e, "getDebugImages");
        }
        // TODO: convert it
        debugImages = new ArrayList<>();
        return debugImages;
    }

    @Override
    public void clearDebugImages() {
        if (!options.isEnableNdk()) {
            return;
        }

        // TODO: synchronize operations as it is a static field
        debugImages = null;

        try {
            moduleLoader.clearModuleList();
        } catch (Exception e) {
            options.getLogger().log(SentryLevel.ERROR, e, "clearDebugImages");
        }
    }
}
