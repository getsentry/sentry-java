package io.sentry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** Factory for creating custom {@link IScopesStorage} implementations. */
@ApiStatus.Experimental
public interface IScopesStorageFactory {
  @NotNull
  IScopesStorage create(@NotNull SentryOptions options);
}
