package io.sentry.util;

import io.sentry.SentryAttribute;
import io.sentry.SentryAttributes;
import io.sentry.SentryEvent;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Utility class for applying logger properties (e.g. MDC) to Sentry events and log attributes. */
@ApiStatus.Internal
public final class LoggerPropertiesUtil {

  /**
   * Applies logger properties from a map to a Sentry event as tags and context. The properties that
   * have keys matching any of the `targetKeys` will be applied as tags, while the others will be
   * reported in the `MDC` context.
   *
   * @param event the Sentry event to add tags to
   * @param targetKeys the list of property keys to apply as tags
   * @param properties the properties map (e.g. MDC) - this map will be modified by removing
   *     properties which were applied as tags
   */
  @ApiStatus.Internal
  public static void applyPropertiesToEvent(
      final @NotNull SentryEvent event,
      final @NotNull List<String> targetKeys,
      final @NotNull Map<String, String> properties) {
    if (!targetKeys.isEmpty() && !properties.isEmpty()) {
      for (final String key : targetKeys) {
        final @Nullable String value = properties.remove(key);
        if (value != null) {
          event.setTag(key, value);
        }
      }
    }
    if (!properties.isEmpty()) {
      event.getContexts().put("MDC", properties);
    }
  }

  /**
   * Applies logger properties from a properties map to SentryAttributes for logs. Only the
   * properties with keys that are found in `targetKeys` will be applied as attributes. Properties
   * with null values are filtered out.
   *
   * @param attributes the SentryAttributes to add the properties to
   * @param targetKeys the list of property keys to apply as attributes
   * @param properties the properties map (e.g. MDC)
   */
  @ApiStatus.Internal
  public static void applyPropertiesToAttributes(
      final @NotNull SentryAttributes attributes,
      final @NotNull List<String> targetKeys,
      final @NotNull Map<String, String> properties) {
    if (!targetKeys.isEmpty() && !properties.isEmpty()) {
      for (final String key : targetKeys) {
        final @Nullable String value = properties.get(key);
        if (value != null) {
          attributes.add(SentryAttribute.stringAttribute("mdc." + key, value));
        }
      }
    }
  }
}
