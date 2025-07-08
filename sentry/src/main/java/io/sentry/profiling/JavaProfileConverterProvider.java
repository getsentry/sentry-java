package io.sentry.profiling;

import io.sentry.IProfileConverter;
import org.jetbrains.annotations.Nullable;

/**
 * Service provider interface for creating profile converters.
 *
 * <p>This interface allows for pluggable profile converter implementations that can be discovered
 * at runtime using the ServiceLoader mechanism.
 */
public interface JavaProfileConverterProvider {

  /**
   * Creates and returns a profile converter instance.
   *
   * @return a profile converter instance, or null if the provider cannot create one
   */
  @Nullable
  IProfileConverter getProfileConverter();
}
