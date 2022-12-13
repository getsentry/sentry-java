package io.sentry.internal.gestures;

import io.sentry.util.Objects;
import java.lang.ref.WeakReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class UiElement {
  final @NotNull WeakReference<Object> viewRef;
  final @Nullable String className;
  final @Nullable String resourceName;
  final @Nullable String tag;

  public UiElement(
      @Nullable Object view,
      @Nullable String className,
      @Nullable String resourceName,
      @Nullable String tag) {
    this.viewRef = new WeakReference<>(view);
    this.className = className;
    this.resourceName = resourceName;
    this.tag = tag;
  }

  public @Nullable String getClassName() {
    return className;
  }

  public @Nullable String getResourceName() {
    return resourceName;
  }

  public @Nullable String getTag() {
    return tag;
  }

  public @NotNull String getIdentifier() {
    // either resourcename or tag is not null
    if (resourceName != null) {
      return resourceName;
    } else {
      return Objects.requireNonNull(tag, "UiElement.tag can't be null");
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    UiElement uiElement = (UiElement) o;

    return Objects.equals(className, uiElement.className)
        && Objects.equals(resourceName, uiElement.resourceName)
        && Objects.equals(tag, uiElement.tag);
  }

  public @Nullable Object getView() {
    return viewRef.get();
  }

  @Override
  public int hashCode() {
    return Objects.hash(viewRef, resourceName, tag);
  }

  public enum Type {
    CLICKABLE,
    SCROLLABLE
  }
}
