package io.sentry;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class FullDisplayedReporter {

  private static final @NotNull FullDisplayedReporter instance = new FullDisplayedReporter();

  private final @NotNull List<FullDisplayedReporterListener> listeners =
      new CopyOnWriteArrayList<>();

  private FullDisplayedReporter() {}

  public static @NotNull FullDisplayedReporter getInstance() {
    return instance;
  }

  public void registerFullyDrawnListener(
      final @NotNull FullDisplayedReporter.FullDisplayedReporterListener listener) {
    listeners.add(listener);
  }

  public void reportFullyDrawn() {
    final @NotNull Iterator<FullDisplayedReporterListener> listenerIterator = listeners.iterator();
    listeners.clear();
    while (listenerIterator.hasNext()) {
      listenerIterator.next().onFullyDrawn();
    }
  }

  @ApiStatus.Internal
  public interface FullDisplayedReporterListener {
    void onFullyDrawn();
  }
}
