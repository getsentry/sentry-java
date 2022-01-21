package io.sentry.android.core.internal.gestures;

import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SearchEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class NoOpWindowCallback implements Window.Callback {
  @Override
  public boolean dispatchKeyEvent(KeyEvent keyEvent) {
    return false;
  }

  @Override
  public boolean dispatchKeyShortcutEvent(KeyEvent keyEvent) {
    return false;
  }

  @Override
  public boolean dispatchTouchEvent(MotionEvent motionEvent) {
    return false;
  }

  @Override
  public boolean dispatchTrackballEvent(MotionEvent motionEvent) {
    return false;
  }

  @Override
  public boolean dispatchGenericMotionEvent(MotionEvent motionEvent) {
    return false;
  }

  @Override
  public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent accessibilityEvent) {
    return false;
  }

  @Nullable
  @Override
  public View onCreatePanelView(int i) {
    return null;
  }

  @Override
  public boolean onCreatePanelMenu(int i, @NonNull Menu menu) {
    return false;
  }

  @Override
  public boolean onPreparePanel(int i, @Nullable View view, @NonNull Menu menu) {
    return false;
  }

  @Override
  public boolean onMenuOpened(int i, @NonNull Menu menu) {
    return false;
  }

  @Override
  public boolean onMenuItemSelected(int i, @NonNull MenuItem menuItem) {
    return false;
  }

  @Override
  public void onWindowAttributesChanged(WindowManager.LayoutParams layoutParams) {}

  @Override
  public void onContentChanged() {}

  @Override
  public void onWindowFocusChanged(boolean b) {}

  @Override
  public void onAttachedToWindow() {}

  @Override
  public void onDetachedFromWindow() {}

  @Override
  public void onPanelClosed(int i, @NonNull Menu menu) {}

  @Override
  public boolean onSearchRequested() {
    return false;
  }

  @Override
  public boolean onSearchRequested(SearchEvent searchEvent) {
    return false;
  }

  @Nullable
  @Override
  public ActionMode onWindowStartingActionMode(ActionMode.Callback callback) {
    return null;
  }

  @Nullable
  @Override
  public ActionMode onWindowStartingActionMode(ActionMode.Callback callback, int i) {
    return null;
  }

  @Override
  public void onActionModeStarted(ActionMode actionMode) {}

  @Override
  public void onActionModeFinished(ActionMode actionMode) {}
}
