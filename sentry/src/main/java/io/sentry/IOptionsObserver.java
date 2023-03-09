package io.sentry;

import io.sentry.protocol.SdkVersion;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A SentryOptions observer that tracks changes of SentryOptions. All methods are "default", so
 * implementors can subscribe to only those properties, that they are interested in.
 */
public interface IOptionsObserver {

  default void setRelease(@Nullable String release) {}

  default void setProguardUuid(@Nullable String proguardUuid) {}

  default void setSdkVersion(@Nullable SdkVersion sdkVersion) {}

  default void setEnvironment(@Nullable String environment) {}

  default void setDist(@Nullable String dist) {}

  default void setTags(@NotNull Map<String, @NotNull String> tags) {}
}
