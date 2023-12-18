package io.sentry.android.core.performance;

import android.view.Window;
import io.sentry.android.core.internal.gestures.WindowCallbackAdapter;
import org.jetbrains.annotations.NotNull;

public class WindowContentChangedCallback extends WindowCallbackAdapter {

  private final @NotNull Runnable callback;

  public WindowContentChangedCallback(
      final @NotNull Window.Callback delegate, final @NotNull Runnable callback) {
    super(delegate);
    this.callback = callback;
  }

  @Override
  public void onContentChanged() {
    super.onContentChanged();
    callback.run();
  }
}
