package io.sentry.protocol;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonSerializable;
import io.sentry.JsonUnknown;
import io.sentry.ObjectReader;
import io.sentry.ObjectWriter;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ViewHierarchy implements JsonUnknown, JsonSerializable {

  public static final class JsonKeys {
    public static final String RENDERING_SYSTEM = "rendering_system";
    public static final String WINDOWS = "windows";
  }

  private final @Nullable String renderingSystem;
  private final @Nullable List<ViewHierarchyNode> windows;
  private @Nullable Map<String, Object> unknown;

  public ViewHierarchy(
      final @Nullable String renderingSystem, final @Nullable List<ViewHierarchyNode> windows) {
    this.renderingSystem = renderingSystem;
    this.windows = windows;
  }

  @Nullable
  public String getRenderingSystem() {
    return renderingSystem;
  }

  @Nullable
  public List<ViewHierarchyNode> getWindows() {
    return windows;
  }

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    if (renderingSystem != null) {
      writer.name(JsonKeys.RENDERING_SYSTEM).value(renderingSystem);
    }
    if (windows != null) {
      writer.name(JsonKeys.WINDOWS).value(logger, windows);
    }
    if (unknown != null) {
      for (String key : unknown.keySet()) {
        Object value = unknown.get(key);
        writer.name(key).value(logger, value);
      }
    }
    writer.endObject();
  }

  @Override
  public @Nullable Map<String, Object> getUnknown() {
    return unknown;
  }

  @Override
  public void setUnknown(@Nullable Map<String, Object> unknown) {
    this.unknown = unknown;
  }

  public static final class Deserializer implements JsonDeserializer<ViewHierarchy> {

    @Override
    public @NotNull ViewHierarchy deserialize(@NotNull ObjectReader reader, @NotNull ILogger logger)
        throws Exception {

      @Nullable String renderingSystem = null;
      @Nullable List<ViewHierarchyNode> windows = null;
      @Nullable Map<String, Object> unknown = null;

      reader.beginObject();
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.RENDERING_SYSTEM:
            renderingSystem = reader.nextStringOrNull();
            break;
          case JsonKeys.WINDOWS:
            windows = reader.nextListOrNull(logger, new ViewHierarchyNode.Deserializer());
            break;
          default:
            if (unknown == null) {
              unknown = new HashMap<>();
            }
            reader.nextUnknown(logger, unknown, nextName);
            break;
        }
      }
      reader.endObject();

      final ViewHierarchy viewHierarchy = new ViewHierarchy(renderingSystem, windows);
      viewHierarchy.setUnknown(unknown);
      return viewHierarchy;
    }
  }
}
