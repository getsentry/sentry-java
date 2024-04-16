package io.sentry;

import io.sentry.util.Objects;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Deduplicates events containing throwable that has been already processed. */
public final class DuplicateEventDetectionEventProcessor implements EventProcessor {
  private final @NotNull Map<Throwable, Object> capturedObjects =
      Collections.synchronizedMap(new WeakHashMap<>());
  private final @NotNull SentryOptions options;

  public DuplicateEventDetectionEventProcessor(final @NotNull SentryOptions options) {
    this.options = Objects.requireNonNull(options, "options are required");
  }

  @Override
  public @Nullable SentryEvent process(final @NotNull SentryEvent event, final @NotNull Hint hint) {
    if (options.isEnableDeduplication()) {
      final Throwable throwable = event.getThrowable();
      if (throwable != null) {
        if (capturedObjects.containsKey(throwable)
            || containsAnyKey(capturedObjects, allCauses(throwable))) {
          options
              .getLogger()
              .log(
                  SentryLevel.DEBUG,
                  "Duplicate Exception detected. Event %s will be discarded.",
                  event.getEventId());
          return null;
        } else {
          capturedObjects.put(throwable, null);
        }
      }
    } else {
      options.getLogger().log(SentryLevel.DEBUG, "Event deduplication is disabled.");
    }
    return event;
  }

  private static <T> boolean containsAnyKey(
      final @NotNull Map<T, Object> map, final @NotNull List<T> list) {
    for (T entry : list) {
      if (map.containsKey(entry)) {
        return true;
      }
    }
    return false;
  }

  private static @NotNull List<Throwable> allCauses(final @NotNull Throwable throwable) {
    final List<Throwable> causes = new ArrayList<>();
    Throwable ex = throwable;
    while (ex.getCause() != null) {
      causes.add(ex.getCause());
      ex = ex.getCause();
    }
    return causes;
  }

  @Override
  public @Nullable Long getOrder() {
    return 1000L;
  }
}
