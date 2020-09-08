package io.sentry.core;

import io.sentry.core.exception.ExceptionMechanismException;
import io.sentry.core.util.Objects;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Deduplicates events containing throwable that has been already processed. */
public final class DuplicateEventDetectionEventProcessor implements EventProcessor {
  private final WeakHashMap<Throwable, Object> capturedObjects = new WeakHashMap<>();
  private final SentryOptions options;

  public DuplicateEventDetectionEventProcessor(final @NotNull SentryOptions options) {
    this.options = Objects.requireNonNull(options, "options are required");
  }

  @Override
  public SentryEvent process(final @NotNull SentryEvent event, final @Nullable Object hint) {
    final Throwable throwable = event.getThrowable();
    if (throwable != null) {
      if (throwable instanceof ExceptionMechanismException) {
        final ExceptionMechanismException ex = (ExceptionMechanismException) throwable;
        if (capturedObjects.containsKey(ex.getThrowable())) {
          options
              .getLogger()
              .log(
                  SentryLevel.DEBUG,
                  "Duplicate Exception detected. Event %s will be discarded.",
                  event.getEventId());
          return null;
        } else {
          capturedObjects.put(ex.getThrowable(), null);
        }
      } else {
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
}
