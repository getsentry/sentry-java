package io.sentry.android.core.internal.gestures;

import android.annotation.SuppressLint;
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
import com.jakewharton.nopen.annotation.Open;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Open
public class WindowCallbackAdapter implements Window.Callback {

  private final @NotNull Window.Callback delegate;

  public WindowCallbackAdapter(final Window.@NotNull Callback delegate) {
    this.delegate = delegate;
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent keyEvent) {
    return delegate.dispatchKeyEvent(keyEvent);
  }

  @Override
  public boolean dispatchKeyShortcutEvent(KeyEvent keyEvent) {
    return delegate.dispatchKeyShortcutEvent(keyEvent);
  }

  @Override
  public boolean dispatchTouchEvent(@Nullable MotionEvent motionEvent) {
    return delegate.dispatchTouchEvent(motionEvent);
  }

  @Override
  public boolean dispatchTrackballEvent(MotionEvent motionEvent) {
    return delegate.dispatchTrackballEvent(motionEvent);
  }

  @Override
  public boolean dispatchGenericMotionEvent(MotionEvent motionEvent) {
    return delegate.dispatchGenericMotionEvent(motionEvent);
  }

  @Override
  public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent accessibilityEvent) {
    return delegate.dispatchPopulateAccessibilityEvent(accessibilityEvent);
  }

  @Nullable
  @Override
  public View onCreatePanelView(int i) {
    return delegate.onCreatePanelView(i);
  }

  @Override
  public boolean onCreatePanelMenu(int i, @NotNull Menu menu) {
    return delegate.onCreatePanelMenu(i, menu);
  }

  @Override
  public boolean onPreparePanel(int i, @Nullable View view, @NotNull Menu menu) {
    return delegate.onPreparePanel(i, view, menu);
  }

  @Override
  public boolean onMenuOpened(int i, @NotNull Menu menu) {
    return delegate.onMenuOpened(i, menu);
  }

  @Override
  public boolean onMenuItemSelected(int i, @NotNull MenuItem menuItem) {
    return delegate.onMenuItemSelected(i, menuItem);
  }

  @Override
  public void onWindowAttributesChanged(WindowManager.LayoutParams layoutParams) {
    delegate.onWindowAttributesChanged(layoutParams);
  }

  @Override
  public void onContentChanged() {
    delegate.onContentChanged();
  }

  @Override
  public void onWindowFocusChanged(boolean b) {
    delegate.onWindowFocusChanged(b);
  }

  @Override
  public void onAttachedToWindow() {
    delegate.onAttachedToWindow();
  }

  @Override
  public void onDetachedFromWindow() {
    delegate.onDetachedFromWindow();
  }

  @Override
  public void onPanelClosed(int i, @NotNull Menu menu) {
    delegate.onPanelClosed(i, menu);
  }

  @Override
  public boolean onSearchRequested() {
    return delegate.onSearchRequested();
  }

  @SuppressLint("NewApi")
  @Override
  public boolean onSearchRequested(SearchEvent searchEvent) {
    return delegate.onSearchRequested(searchEvent);
  }

  @Nullable
  @Override
  public ActionMode onWindowStartingActionMode(ActionMode.Callback callback) {
    return delegate.onWindowStartingActionMode(callback);
  }

  @SuppressLint("NewApi")
  @Nullable
  @Override
  public ActionMode onWindowStartingActionMode(ActionMode.Callback callback, int i) {
    return delegate.onWindowStartingActionMode(callback, i);
  }

  @Override
  public void onActionModeStarted(ActionMode actionMode) {
    delegate.onActionModeStarted(actionMode);
  }

  @Override
  public void onActionModeFinished(ActionMode actionMode) {
    delegate.onActionModeFinished(actionMode);
  }
}
