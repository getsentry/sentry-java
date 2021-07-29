package io.sentry.protocol;

import io.sentry.SessionAggregates;
import io.sentry.util.Objects;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Container for server-side aggregated sessions. */
public final class Sessions {
  private final @NotNull List<Aggregate> aggregates = new ArrayList<>();
  private final @NotNull Attributes attrs;

  public Sessions(final @NotNull SessionAggregates sessionAggregates) {
    Objects.requireNonNull(sessionAggregates, "sessionAggregates is required");
    this.attrs =
        new Attributes(
            sessionAggregates.getAttributes().getRelease(),
            sessionAggregates.getAttributes().getEnvironment());
    for (final Map.Entry<String, SessionAggregates.SessionStats> entry :
        sessionAggregates.getAggregates().entrySet()) {
      aggregates.add(
          new Aggregate(
              entry.getKey(),
              entry.getValue().getExited().get(),
              entry.getValue().getErrored().get(),
              entry.getValue().getCrashed().get()));
    }
  }

  public @NotNull List<Aggregate> getAggregates() {
    return aggregates;
  }

  public @NotNull Attributes getAttrs() {
    return attrs;
  }

  public static final class Attributes {
    private final @NotNull String release;
    private final @Nullable String environment;

    public Attributes(final @NotNull String release, final @Nullable String environment) {
      this.release = release;
      this.environment = environment;
    }

    public @NotNull String getRelease() {
      return release;
    }

    public @Nullable String getEnvironment() {
      return environment;
    }
  }

  public static final class Aggregate {
    private final @NotNull String started;
    private final long exited;
    private final long errored;
    private final long crashed;

    public Aggregate(final @NotNull String started, long exited, long errored, long crashed) {
      this.started = started;
      this.exited = exited;
      this.errored = errored;
      this.crashed = crashed;
    }

    public @NotNull String getStarted() {
      return started;
    }

    public long getExited() {
      return exited;
    }

    public long getErrored() {
      return errored;
    }

    public long getCrashed() {
      return crashed;
    }
  }
}
