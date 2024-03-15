package io.sentry;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DefaultScopeStorage implements ScopeStorage {

  // TODO singleton?

  private static final @NotNull ThreadLocal<IHub> currentHub = new ThreadLocal<>();

  @Override
  public SentryStorageToken set(@Nullable IHub hub) {
    final @Nullable IHub oldHub = get();
    currentHub.set(hub);
    return new DefaultScopeStorageToken(oldHub);
  }

  @Override
  public @Nullable IHub get() {
    return currentHub.get();
  }

  @Override
  public void close() {
    // TODO prevent further storing? would this cause problems if singleton, closed and
    // re-initialized?
    currentHub.remove();
  }

  static final class DefaultScopeStorageToken implements SentryStorageToken {

    private final @Nullable IHub oldValue;

    DefaultScopeStorageToken(final @Nullable IHub hub) {
      this.oldValue = hub;
    }

    @Override
    public void close() {
      currentHub.set(oldValue);
    }
  }
}
