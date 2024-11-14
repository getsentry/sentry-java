package io.sentry;

import io.sentry.protocol.SentryId;
import io.sentry.util.CollectionUtils;
import io.sentry.util.Objects;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ProfileContext implements JsonUnknown, JsonSerializable {
  public static final String TYPE = "profile";

  /** Determines which trace the Span belongs to. */
  private @NotNull SentryId profilerId;

  private @Nullable Map<String, Object> unknown;

  public ProfileContext() {
    this(SentryId.EMPTY_ID);
  }

  public ProfileContext(final @NotNull SentryId profilerId) {
    this.profilerId = profilerId;
  }

  /**
   * Copy constructor.
   *
   * @param profileContext the ProfileContext to copy
   */
  public ProfileContext(final @NotNull ProfileContext profileContext) {
    this.profilerId = profileContext.profilerId;
    final Map<String, Object> copiedUnknown =
        CollectionUtils.newConcurrentHashMap(profileContext.unknown);
    if (copiedUnknown != null) {
      this.unknown = copiedUnknown;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ProfileContext)) return false;
    ProfileContext that = (ProfileContext) o;
    return profilerId.equals(that.profilerId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(profilerId);
  }

  // region JsonSerializable

  public static final class JsonKeys {
    public static final String PROFILER_ID = "profiler_id";
  }

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    writer.name(JsonKeys.PROFILER_ID).value(logger, profilerId);
    if (unknown != null) {
      for (String key : unknown.keySet()) {
        Object value = unknown.get(key);
        writer.name(key).value(logger, value);
      }
    }
    writer.endObject();
  }

  public @NotNull SentryId getProfilerId() {
    return profilerId;
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

  public static final class Deserializer implements JsonDeserializer<ProfileContext> {
    @Override
    public @NotNull ProfileContext deserialize(
        @NotNull ObjectReader reader, @NotNull ILogger logger) throws Exception {
      reader.beginObject();
      ProfileContext data = new ProfileContext();
      Map<String, Object> unknown = null;
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.PROFILER_ID:
            SentryId profilerId = reader.nextOrNull(logger, new SentryId.Deserializer());
            if (profilerId != null) {
              data.profilerId = profilerId;
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
      data.setUnknown(unknown);
      reader.endObject();
      return data;
    }
  }
}
