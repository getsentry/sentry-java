package io.sentry.config;

import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Wrapper around multiple {@link PropertiesProvider}. It tries to resolve property from the first
 * provider to the last. Returns {@code null} if property has not been found in any of the
 * configured providers.
 */
final class CompositePropertiesProvider implements PropertiesProvider {
  private final @NotNull List<PropertiesProvider> providers;

  public CompositePropertiesProvider(@NotNull List<PropertiesProvider> providers) {
    this.providers = providers;
  }

  @Override
  public @Nullable String getProperty(@NotNull String property) {
    for (final PropertiesProvider provider : providers) {
      final String result = provider.getProperty(property);
      if (result != null) {
        return result;
      }
    }
    return null;
  }
}
