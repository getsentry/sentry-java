package io.sentry;

import io.sentry.util.Objects;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/** Deduplicates events containing throwable that has been already processed. */
public final class DuplicateEventDetectionEventProcessor implements EventProcessor {
  private final ConcurrentLinkedDeque<Throwable> capturedObjects = new ConcurrentLinkedDeque<>();
  private final SentryOptions options;
  private final int bufferSize;

  public DuplicateEventDetectionEventProcessor(final @NotNull SentryOptions options) {
    this(options, 100);
  }

  public DuplicateEventDetectionEventProcessor(final @NotNull SentryOptions options, int bufferSize) {
    this.options = Objects.requireNonNull(options, "options are required");
    this.bufferSize = bufferSize;
  }

  @Override
  public SentryEvent process(final @NotNull SentryEvent event, final @Nullable Object hint) {
    final Throwable throwable = event.getOriginThrowable();
    if (throwable != null) {
      if (capturedObjects.contains(throwable)
          || containsAnyKey(capturedObjects, allCauses(throwable))) {
        options
            .getLogger()
            .log(
                SentryLevel.DEBUG,
                "Duplicate Exception detected. Event %s will be discarded.",
                event.getEventId());
        return null;
      } else {
        capturedObjects.add(throwable);
        if (capturedObjects.size() > bufferSize) {
          capturedObjects.poll();
        }
      }
    }
    return event;
  }

  @TestOnly
  int size() {
    return capturedObjects.size();
  }

  private static <T> boolean containsAnyKey(
    final @NotNull Collection<T> map, final @NotNull List<T> list) {
    for (T entry : list) {
      if (map.contains(entry)) {
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
