package io.sentry.spring;

import io.sentry.EventProcessor;
import io.sentry.Hint;
import io.sentry.SentryEvent;
import io.sentry.SentryOptions;
import io.sentry.util.CollectionUtils;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.MDC;

/**
 * Attaches context tags defined in {@link SentryOptions#getContextTags()} from {@link MDC} to
 * {@link SentryEvent#getTags()}.
 */
public final class ContextTagsEventProcessor implements EventProcessor {
  private final SentryOptions options;

  public ContextTagsEventProcessor(final @NotNull SentryOptions options) {
    this.options = options;
  }

  @Override
  public @NotNull SentryEvent process(@NotNull SentryEvent event, @Nullable Hint hint) {
    final Map<String, String> contextMap = MDC.getCopyOfContextMap();
    if (contextMap != null) {
      final Map<String, String> mdcProperties =
          CollectionUtils.filterMapEntries(contextMap, entry -> entry.getValue() != null);
      if (!mdcProperties.isEmpty() && !options.getContextTags().isEmpty()) {
        for (final String contextTag : options.getContextTags()) {
          // if mdc tag is listed in SentryOptions, apply as event tag
          if (mdcProperties.containsKey(contextTag)) {
            event.setTag(contextTag, mdcProperties.get(contextTag));
          }
        }
      }
    }
    return event;
  }

  @Override
  public @Nullable Long getOrder() {
    return 14000L;
  }
}
