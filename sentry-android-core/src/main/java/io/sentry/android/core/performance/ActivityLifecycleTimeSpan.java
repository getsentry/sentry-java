package io.sentry.android.core.performance;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class ActivityLifecycleTimeSpan implements Comparable<ActivityLifecycleTimeSpan> {
  public final @NotNull TimeSpan onCreate = new TimeSpan();
  public final @NotNull TimeSpan onStart = new TimeSpan();

  @Override
  public int compareTo(ActivityLifecycleTimeSpan o) {
    return Long.compare(onCreate.getStartUptimeMs(), o.onCreate.getStartUptimeMs());
  }
}
