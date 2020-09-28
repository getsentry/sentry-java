package io.sentry.config;

import java.util.Properties;
import org.jetbrains.annotations.Nullable;

/** Loads {@link Properties} from external source. */
interface PropertiesLoader {
  @Nullable
  Properties load();
}
