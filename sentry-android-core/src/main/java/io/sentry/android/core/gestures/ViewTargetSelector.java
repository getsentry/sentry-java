package io.sentry.android.core.gestures;

import android.view.View;
import org.jetbrains.annotations.NotNull;

interface ViewTargetSelector {
  boolean select(@NotNull View view);
}
