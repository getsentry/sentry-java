package io.sentry.internal.gestures;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface GestureTargetLocator {

  @Nullable
  UiElement locate(
      final @NotNull Object root, final float x, final float y, final UiElement.Type targetType);
}
