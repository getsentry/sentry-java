package io.sentry;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class SpanOptions {

  private final boolean trimStart;
  private final boolean trimEnd;
  private final boolean autoFinish;

  public SpanOptions() {
    this(false, false, false);
  }

  /**
   * @param trimStart true if the start time should be trimmed to the minimum start time of it's
   *     children
   * @param trimEnd true if the end time should be trimmed to the maximum end time of it's children
   * @param autoFinish true if this span should be finished whenever the root transaction gets
   *     finished
   */
  public SpanOptions(final boolean trimStart, final boolean trimEnd, final boolean autoFinish) {
    this.trimStart = trimStart;
    this.trimEnd = trimEnd;
    this.autoFinish = autoFinish;
  }

  public boolean isTrimStart() {
    return trimStart;
  }

  public boolean isTrimEnd() {
    return trimEnd;
  }

  public boolean isAutoFinish() {
    return autoFinish;
  }
}
