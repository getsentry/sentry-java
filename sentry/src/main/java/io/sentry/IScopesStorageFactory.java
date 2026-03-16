package io.sentry;

import io.sentry.util.LoadClass;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** Factory for creating custom {@link IScopesStorage} implementations. */
@ApiStatus.Experimental
public interface IScopesStorageFactory {
  @NotNull
  IScopesStorage create(final @NotNull LoadClass loadClass, final @NotNull ILogger logger);
}
