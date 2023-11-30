package io.sentry.android.core.performance;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class ActivityLifecycleTimeSpan implements Comparable<ActivityLifecycleTimeSpan> {
  private final @NotNull TimeSpan onCreate = new TimeSpan();
  private final @NotNull TimeSpan onStart = new TimeSpan();

  public final @NotNull TimeSpan getOnCreate() {
    return onCreate;
  }

  public final @NotNull TimeSpan getOnStart() {
    return onStart;
  }

  @Override
  public int compareTo(ActivityLifecycleTimeSpan o) {
    final int onCreateDiff =
        Long.compare(onCreate.getStartUptimeMs(), o.onCreate.getStartUptimeMs());
    if (onCreateDiff == 0) {
      return Long.compare(onStart.getStartUptimeMs(), o.onStart.getStartUptimeMs());
    } else {
      return onCreateDiff;
    }
  }
}
