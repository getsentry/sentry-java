package io.sentry;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class FullyDisplayedReporter {

  private static final @NotNull FullyDisplayedReporter instance = new FullyDisplayedReporter();

  private final @NotNull List<FullyDisplayedReporterListener> listeners =
      new CopyOnWriteArrayList<>();

  private FullyDisplayedReporter() {}

  public static @NotNull FullyDisplayedReporter getInstance() {
    return instance;
  }

  public void registerFullyDrawnListener(
      final @NotNull FullyDisplayedReporter.FullyDisplayedReporterListener listener) {
    listeners.add(listener);
  }

  public void reportFullyDrawn() {
    final @NotNull Iterator<FullyDisplayedReporterListener> listenerIterator = listeners.iterator();
    listeners.clear();
    while (listenerIterator.hasNext()) {
      listenerIterator.next().onFullyDrawn();
    }
  }

  @ApiStatus.Internal
  public interface FullyDisplayedReporterListener {
    void onFullyDrawn();
  }
}
