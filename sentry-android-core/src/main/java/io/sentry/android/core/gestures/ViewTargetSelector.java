package io.sentry.android.core.gestures;

import android.view.View;
import org.jetbrains.annotations.NotNull;

interface ViewTargetSelector {
  /**
   * Defines whether the given {@code view} should be selected from the view hierarchy.
   *
   * @param view - the view to be selected.
   * @return true, when the view should be selected, false otherwise.
   */
  boolean select(@NotNull View view);

  /**
   * Defines whether the view from the select method is eligible for children traversal, in case
   * it's a ViewGroup.
   *
   * @return true, when the ViewGroup is sufficient to be selected and children traversal is not
   * necessary.
   */
  default boolean skipChildren() {
    return false;
  }
}
