package io.sentry.internal.gestures;

import java.lang.ref.WeakReference;
import java.util.Objects;
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
      return Objects.requireNonNull(tag);
    }
  }

  public @Nullable Object getView() {
    return viewRef.get();
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(viewRef, resourceName, tag);
  }

  public enum Type {
    CLICKABLE,
    SCROLLABLE
  }
}
