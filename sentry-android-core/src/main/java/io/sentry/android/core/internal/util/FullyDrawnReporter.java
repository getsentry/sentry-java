package io.sentry.android.core.internal.util;

import android.app.Activity;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class FullyDrawnReporter {

  private static final @NotNull FullyDrawnReporter instance = new FullyDrawnReporter();
  private final @NotNull Map<String, FullyDrawnReporterListener> listeners =
      new ConcurrentHashMap<>();

  private FullyDrawnReporter() {}

  public static @NotNull FullyDrawnReporter getInstance() {
    return instance;
  }

  public void registerFullyDrawnListener(@NotNull final FullyDrawnReporterListener listener) {
    listeners.put(listener.uuid, listener);
  }

  public void reportFullyDrawn(@NotNull final Activity reportedActivity) {
    for (FullyDrawnReporterListener listener : listeners.values()) {
      if (listener.onFullyDrawn(reportedActivity)) {
        listeners.remove(listener.uuid);
      }
    }
  }

  @ApiStatus.Internal
  public abstract static class FullyDrawnReporterListener {
    @NotNull final String uuid = UUID.randomUUID().toString();

    public abstract boolean onFullyDrawn(@NotNull final Activity reportedActivity);
  }
}
