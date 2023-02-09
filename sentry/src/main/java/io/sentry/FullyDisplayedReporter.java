package io.sentry;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class FullyDisplayedReporter {

  private static final @NotNull FullyDisplayedReporter instance = new FullyDisplayedReporter();

  private final @NotNull List<FullyDrawnReporterListener> listeners = new ArrayList<>();

  private FullyDisplayedReporter() {}

  public static @NotNull FullyDisplayedReporter getInstance() {
    return instance;
  }

  public void registerFullyDrawnListener(final @NotNull FullyDrawnReporterListener listener) {
    synchronized (listeners) {
      listeners.add(listener);
    }
  }

  public void reportFullyDrawn() {
    synchronized (listeners) {
      final @NotNull Iterator<FullyDrawnReporterListener> listenerIterator = listeners.iterator();
      while (listenerIterator.hasNext()) {
        listenerIterator.next().onFullyDrawn();
        listenerIterator.remove();
      }
    }
  }

  @ApiStatus.Internal
  public interface FullyDrawnReporterListener {
    void onFullyDrawn();
  }
}
