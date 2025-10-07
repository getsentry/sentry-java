package io.sentry.util;

import io.sentry.SentryAttribute;
import io.sentry.SentryAttributes;
import io.sentry.SentryEvent;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** Utility class for applying context tags (e.g. MDC) to Sentry events and log attributes. */
@ApiStatus.Internal
public final class ContextTagsUtil {

  /**
   * Applies context tags from a properties map to a Sentry event as tags. Context tags that are
   * found in the properties map are set as event tags and removed from the properties map.
   *
   * @param event the Sentry event to add tags to
   * @param contextTags the list of context tag names to look for
   * @param properties the properties map (e.g. MDC) - this map will be modified by removing applied
   *     context tags
   */
  @ApiStatus.Internal
  public static void applyContextTagsToEvent(
      final @NotNull SentryEvent event,
      final @NotNull List<String> contextTags,
      final @NotNull Map<String, String> properties) {
    if (!contextTags.isEmpty() && !properties.isEmpty()) {
      for (final String contextTag : contextTags) {
        if (properties.containsKey(contextTag)) {
          event.setTag(contextTag, properties.get(contextTag));
          properties.remove(contextTag);
        }
      }
    }
  }

  /**
   * Applies context tags from a properties map to SentryAttributes for logs. Properties with null
   * values are filtered out.
   *
   * @param attributes the SentryAttributes to add context tags to
   * @param properties the properties map (e.g. MDC)
   */
  @ApiStatus.Internal
  public static void applyContextTagsToLogAttributes(
      final @NotNull SentryAttributes attributes, final @NotNull Map<String, String> properties) {
    for (final Map.Entry<String, String> entry : properties.entrySet()) {
      if (entry.getValue() != null) {
        attributes.add(SentryAttribute.stringAttribute(entry.getKey(), entry.getValue()));
      }
    }
  }
}
