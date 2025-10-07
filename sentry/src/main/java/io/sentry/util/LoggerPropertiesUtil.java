package io.sentry.util;

import io.sentry.SentryAttribute;
import io.sentry.SentryAttributes;
import io.sentry.SentryEvent;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** Utility class for applying logger properties (e.g. MDC) to Sentry events and log attributes. */
@ApiStatus.Internal
public final class LoggerPropertiesUtil {

  /**
   * Applies logger properties from a map to a Sentry event as tags. Only the properties with keys
   * that are found in `targetKeys` will be applied as tags.
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
        if (properties.containsKey(key)) {
          event.setTag(key, properties.get(key));
          properties.remove(key);
        }
      }
    }
  }

  /**
   * Applies logger properties from a properties map to SentryAttributes for logs. Properties with
   * null values are filtered out.
   *
   * @param attributes the SentryAttributes to add the properties to
   * @param properties the properties map (e.g. MDC)
   */
  @ApiStatus.Internal
  public static void applyPropertiesToAttributes(
      final @NotNull SentryAttributes attributes, final @NotNull Map<String, String> properties) {
    for (final Map.Entry<String, String> entry : properties.entrySet()) {
      if (entry.getValue() != null) {
        attributes.add(SentryAttribute.stringAttribute(entry.getKey(), entry.getValue()));
      }
    }
  }
}
