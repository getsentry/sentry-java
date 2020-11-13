package io.sentry.android.core;

import java.util.List;

import io.sentry.protocol.DebugImage;

public interface IDebugImagesLoader {
    List<DebugImage> getDebugImages();

    void clearDebugImages();
}
