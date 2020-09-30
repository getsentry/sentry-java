package io.sentry.config;

import java.util.Properties;
import org.jetbrains.annotations.Nullable;

interface PropertiesLoader {
  /**
   * Loads {@link Properties} from external source.
   *
   * @return the properties or null if failed to load.
   */
  @Nullable
  Properties load();
}
