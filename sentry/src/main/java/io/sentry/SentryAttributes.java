package io.sentry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SentryAttributes {

  private final @NotNull Map<String, SentryAttribute> attributes;

  private SentryAttributes(final @NotNull Map<String, SentryAttribute> attributes) {
    this.attributes = attributes;
  }

  public void add(final @Nullable SentryAttribute attribute) {
    if (attribute == null) {
      return;
    }
    attributes.put(attribute.getName(), attribute);
  }

  public @NotNull Map<String, SentryAttribute> getAttributes() {
    return attributes;
  }

  public static @NotNull SentryAttributes of(final @Nullable SentryAttribute... attributes) {
    if (attributes == null) {
      return new SentryAttributes(new ConcurrentHashMap<>());
    }
    final @NotNull SentryAttributes sentryAttributes =
        new SentryAttributes(new ConcurrentHashMap<>(attributes.length));
    for (SentryAttribute attribute : attributes) {
      sentryAttributes.add(attribute);
    }
    return sentryAttributes;
  }

  public static @NotNull SentryAttributes fromMap(final @Nullable Map<String, Object> attributes) {
    if (attributes == null) {
      return new SentryAttributes(new ConcurrentHashMap<>());
    }
    final @NotNull SentryAttributes sentryAttributes =
        new SentryAttributes(new ConcurrentHashMap<>(attributes.size()));
    for (Map.Entry<String, Object> attribute : attributes.entrySet()) {
      final @Nullable String key = attribute.getKey();
      if (key != null) {
        sentryAttributes.add(SentryAttribute.named(key, attribute.getValue()));
      }
    }

    return sentryAttributes;
  }
}
