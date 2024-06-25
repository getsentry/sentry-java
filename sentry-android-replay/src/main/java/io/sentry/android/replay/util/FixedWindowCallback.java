/**
 * Adapted from https://github.com/square/curtains/tree/v1.2.5
 *
 * <p>Copyright 2021 Square Inc.
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sentry.android.replay.util;

import android.annotation.SuppressLint;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.KeyboardShortcutGroup;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SearchEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implementation of Window.Callback that updates the signature of {@link #onMenuOpened(int, Menu)}
 * to change the menu param from non null to nullable to avoid runtime null check crashes. Issue:
 * https://issuetracker.google.com/issues/188568911
 */
public class FixedWindowCallback implements Window.Callback {

  public final @Nullable Window.Callback delegate;

  public FixedWindowCallback(@Nullable Window.Callback delegate) {
    this.delegate = delegate;
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    if (delegate == null) {
      return false;
    }
    return delegate.dispatchKeyEvent(event);
  }

  @Override
  public boolean dispatchKeyShortcutEvent(KeyEvent event) {
    if (delegate == null) {
      return false;
    }
    return delegate.dispatchKeyShortcutEvent(event);
  }

  @Override
  public boolean dispatchTouchEvent(MotionEvent event) {
    if (delegate == null) {
      return false;
    }
    return delegate.dispatchTouchEvent(event);
  }

  @Override
  public boolean dispatchTrackballEvent(MotionEvent event) {
    if (delegate == null) {
      return false;
    }
    return delegate.dispatchTrackballEvent(event);
  }

  @Override
  public boolean dispatchGenericMotionEvent(MotionEvent event) {
    if (delegate == null) {
      return false;
    }
    return delegate.dispatchGenericMotionEvent(event);
  }

  @Override
  public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
    if (delegate == null) {
      return false;
    }
    return delegate.dispatchPopulateAccessibilityEvent(event);
  }

  @Nullable
  @Override
  public View onCreatePanelView(int featureId) {
    if (delegate == null) {
      return null;
    }
    return delegate.onCreatePanelView(featureId);
  }

  @Override
  public boolean onCreatePanelMenu(int featureId, @NotNull Menu menu) {
    if (delegate == null) {
      return false;
    }
    return delegate.onCreatePanelMenu(featureId, menu);
  }

  @Override
  public boolean onPreparePanel(int featureId, @Nullable View view, @NotNull Menu menu) {
    if (delegate == null) {
      return false;
    }
    return delegate.onPreparePanel(featureId, view, menu);
  }

  @Override
  public boolean onMenuOpened(int featureId, @Nullable Menu menu) {
    if (delegate == null) {
      return false;
    }
    return delegate.onMenuOpened(featureId, menu);
  }

  @Override
  public boolean onMenuItemSelected(int featureId, @NotNull MenuItem item) {
    if (delegate == null) {
      return false;
    }
    return delegate.onMenuItemSelected(featureId, item);
  }

  @Override
  public void onWindowAttributesChanged(WindowManager.LayoutParams attrs) {
    if (delegate == null) {
      return;
    }
    delegate.onWindowAttributesChanged(attrs);
  }

  @Override
  public void onContentChanged() {
    if (delegate == null) {
      return;
    }
    delegate.onContentChanged();
  }

  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    if (delegate == null) {
      return;
    }
    delegate.onWindowFocusChanged(hasFocus);
  }

  @Override
  public void onAttachedToWindow() {
    if (delegate == null) {
      return;
    }
    delegate.onAttachedToWindow();
  }

  @Override
  public void onDetachedFromWindow() {
    if (delegate == null) {
      return;
    }
    delegate.onDetachedFromWindow();
  }

  @Override
  public void onPanelClosed(int featureId, @NotNull Menu menu) {
    if (delegate == null) {
      return;
    }
    delegate.onPanelClosed(featureId, menu);
  }

  @Override
  public boolean onSearchRequested() {
    if (delegate == null) {
      return false;
    }
    return delegate.onSearchRequested();
  }

  @SuppressLint("NewApi")
  @Override
  public boolean onSearchRequested(SearchEvent searchEvent) {
    if (delegate == null) {
      return false;
    }
    return delegate.onSearchRequested(searchEvent);
  }

  @Nullable
  @Override
  public ActionMode onWindowStartingActionMode(ActionMode.Callback callback) {
    if (delegate == null) {
      return null;
    }
    return delegate.onWindowStartingActionMode(callback);
  }

  @SuppressLint("NewApi")
  @Nullable
  @Override
  public ActionMode onWindowStartingActionMode(ActionMode.Callback callback, int type) {
    if (delegate == null) {
      return null;
    }
    return delegate.onWindowStartingActionMode(callback, type);
  }

  @Override
  public void onActionModeStarted(ActionMode mode) {
    if (delegate == null) {
      return;
    }
    delegate.onActionModeStarted(mode);
  }

  @Override
  public void onActionModeFinished(ActionMode mode) {
    if (delegate == null) {
      return;
    }
    delegate.onActionModeFinished(mode);
  }

  @SuppressLint("NewApi")
  @Override
  public void onProvideKeyboardShortcuts(
      List<KeyboardShortcutGroup> data, @Nullable Menu menu, int deviceId) {
    if (delegate == null) {
      return;
    }
    delegate.onProvideKeyboardShortcuts(data, menu, deviceId);
  }

  @SuppressLint("NewApi")
  @Override
  public void onPointerCaptureChanged(boolean hasCapture) {
    if (delegate == null) {
      return;
    }
    delegate.onPointerCaptureChanged(hasCapture);
  }
}
