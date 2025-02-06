package io.sentry.internal.gestures;

import org.jetbrains.annotations.Nullable;

public interface GestureTargetLocator {

  @Nullable
  UiElement locate(
      final @Nullable Object root, final float x, final float y, final UiElement.Type targetType);
}
