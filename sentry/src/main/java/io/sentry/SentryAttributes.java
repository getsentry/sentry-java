package io.sentry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SentryAttributes {

  private final @NotNull List<SentryAttribute> attributes;

  private SentryAttributes(final @NotNull List<SentryAttribute> attributes) {
    this.attributes = attributes;
  }

  public void add(final @Nullable SentryAttribute attribute) {
    if (attribute == null) {
      return;
    }
    attributes.add(attribute);
  }

  public @NotNull List<SentryAttribute> getAttributes() {
    return attributes;
  }

  public static @NotNull SentryAttributes of(SentryAttribute... attributes) {
    return new SentryAttributes(Arrays.asList(attributes));
  }

  public static @NotNull SentryAttributes fromMap(final @Nullable Map<String, Object> attributes) {
    if (attributes == null) {
      return new SentryAttributes(new ArrayList<>());
    }
    SentryAttributes sentryAttributes = new SentryAttributes(new ArrayList<>(attributes.size()));
    for (Map.Entry<String, Object> attribute : attributes.entrySet()) {
      sentryAttributes.add(SentryAttribute.named(attribute.getKey(), attribute.getValue()));
    }

    return sentryAttributes;
  }
}
