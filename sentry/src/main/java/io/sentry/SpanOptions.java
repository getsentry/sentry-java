package io.sentry;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class SpanOptions {

  private final boolean trimStart;
  private final boolean trimEnd;
  private final boolean removeIfNoChildren;
  private final boolean autoFinish;

  public SpanOptions() {
    this(false, false, false, false);
  }

  public SpanOptions(
      final boolean trimStart,
      final boolean trimEnd,
      final boolean removeIfNoChildren,
      final boolean autoFinish) {
    this.trimStart = trimStart;
    this.trimEnd = trimEnd;
    this.removeIfNoChildren = removeIfNoChildren;
    this.autoFinish = autoFinish;
  }

  public boolean isTrimStart() {
    return trimStart;
  }

  public boolean isTrimEnd() {
    return trimEnd;
  }

  public boolean isRemoveIfNoChildren() {
    return removeIfNoChildren;
  }

  public boolean isAutoFinish() {
    return autoFinish;
  }
}
