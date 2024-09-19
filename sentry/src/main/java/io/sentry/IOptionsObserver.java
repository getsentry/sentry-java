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

  void setRelease(@Nullable String release);

  void setProguardUuid(@Nullable String proguardUuid);

  void setSdkVersion(@Nullable SdkVersion sdkVersion);

  void setEnvironment(@Nullable String environment);

  void setDist(@Nullable String dist);

  void setTags(@NotNull Map<String, @NotNull String> tags);

  void setReplayErrorSampleRate(@Nullable Double replayErrorSampleRate);
}
