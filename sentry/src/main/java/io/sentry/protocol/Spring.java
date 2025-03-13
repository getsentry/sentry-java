package io.sentry.protocol;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonSerializable;
import io.sentry.JsonUnknown;
import io.sentry.ObjectReader;
import io.sentry.ObjectWriter;
import io.sentry.util.CollectionUtils;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Spring implements JsonUnknown, JsonSerializable {
  public static final String TYPE = "spring";

  /**
   * The names of the active Spring profiles. Is an empty list if the only active profile is the
   * default one.
   */
  private @Nullable String[] activeProfiles;

  /** Unknown fields, only for internal usage. */
  private @Nullable Map<String, @NotNull Object> unknown;

  public Spring() {}

  public Spring(final @NotNull Spring spring) {
    this.activeProfiles = spring.activeProfiles;
    this.unknown = CollectionUtils.newConcurrentHashMap(spring.unknown);
  }

  public @Nullable String[] getActiveProfiles() {
    return activeProfiles;
  }

  public void setActiveProfiles(final @Nullable String[] activeProfiles) {
    this.activeProfiles = activeProfiles;
  }

  @Nullable
  @Override
  public Map<String, Object> getUnknown() {
    return unknown;
  }

  @Override
  public void setUnknown(@Nullable Map<String, Object> unknown) {
    this.unknown = unknown;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Spring spring = (Spring) o;
    return Arrays.equals(activeProfiles, spring.activeProfiles);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(activeProfiles);
  }

  public static final class JsonKeys {
    public static final String ACTIVE_PROFILES = "active_profiles";
  }

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    if (activeProfiles != null) {
      writer.name(JsonKeys.ACTIVE_PROFILES).value(logger, activeProfiles);
    }
    if (unknown != null) {
      for (String key : unknown.keySet()) {
        Object value = unknown.get(key);
        writer.name(key);
        writer.value(logger, value);
      }
    }
    writer.endObject();
  }

  public static final class Deserializer implements JsonDeserializer<Spring> {
    @Override
    public @NotNull Spring deserialize(@NotNull ObjectReader reader, @NotNull ILogger logger)
        throws Exception {
      reader.beginObject();
      Spring spring = new Spring();
      Map<String, Object> unknown = null;
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.ACTIVE_PROFILES:
            List<?> activeProfilesList = (List<?>) reader.nextObjectOrNull();
            if (activeProfilesList != null) {
              Object[] activeProfiles = new String[activeProfilesList.size()];
              activeProfilesList.toArray(activeProfiles);
              spring.activeProfiles = (String[]) activeProfiles;
            }
            break;
          default:
            if (unknown == null) {
              unknown = new ConcurrentHashMap<>();
            }
            reader.nextUnknown(logger, unknown, nextName);
            break;
        }
      }
      spring.setUnknown(unknown);
      reader.endObject();
      return spring;
    }
  }
}
