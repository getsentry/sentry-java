package io.sentry;

import org.jetbrains.annotations.Nullable;

public interface ScopeStorage {

  // TODO use Scopes instead
  SentryStorageToken set(final @Nullable IHub hub);

  // TODO use scopes
  @Nullable
  IHub get();

  // TODO do we need this?
  void close();
}
