package io.sentry.android.core.anr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class AnrProfile {
  public final List<AnrStackTrace> stacks;

  public final long startTimeMs;
  public final long endTimeMs;

  public AnrProfile(List<AnrStackTrace> stacks) {
    this.stacks = new ArrayList<>(stacks.size());
    for (AnrStackTrace stack : stacks) {
      if (stack != null) {
        this.stacks.add(stack);
      }
    }
    Collections.sort(this.stacks);

    if (!this.stacks.isEmpty()) {
      startTimeMs = this.stacks.get(0).timestampMs;

      // adding 10s to be less strict around end time
      endTimeMs = this.stacks.get(this.stacks.size() - 1).timestampMs + 10_000L;
    } else {
      startTimeMs = 0L;
      endTimeMs = 0L;
    }
  }
}
