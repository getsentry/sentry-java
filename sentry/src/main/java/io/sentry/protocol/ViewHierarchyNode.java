package io.sentry.protocol;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonObjectReader;
import io.sentry.JsonObjectWriter;
import io.sentry.JsonSerializable;
import io.sentry.JsonUnknown;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ViewHierarchyNode implements JsonUnknown, JsonSerializable {

  public static final class JsonKeys {
    public static final String RENDERING_SYSTEM = "rendering_system";
    public static final String TYPE = "type";
    public static final String IDENTIFIER = "identifier";
    public static final String TAG = "tag";
    public static final String WIDTH = "width";
    public static final String HEIGHT = "height";
    public static final String X = "x";
    public static final String Y = "y";
    public static final String VISIBLE = "visible";
    public static final String ALPHA = "alpha";
    public static final String CHILDREN = "children";
  }

  private @Nullable String renderingSystem;
  private @Nullable String type;
  private @Nullable String identifier;
  private @Nullable String tag;
  private @Nullable Double width;
  private @Nullable Double height;
  private @Nullable Double x;
  private @Nullable Double y;
  private @Nullable Boolean visible;
  private @Nullable Double alpha;
  private @Nullable List<ViewHierarchyNode> children;
  private @Nullable Map<String, Object> unknown;

  public ViewHierarchyNode() {}

  public void setRenderingSystem(String renderingSystem) {
    this.renderingSystem = renderingSystem;
  }

  public void setType(String type) {
    this.type = type;
  }

  public void setIdentifier(final @Nullable String identifier) {
    this.identifier = identifier;
  }

  public void setTag(final @Nullable String tag) {
    this.tag = tag;
  }

  public void setWidth(final @Nullable Double width) {
    this.width = width;
  }

  public void setHeight(final @Nullable Double height) {
    this.height = height;
  }

  public void setX(final @Nullable Double x) {
    this.x = x;
  }

  public void setY(final @Nullable Double y) {
    this.y = y;
  }

  public void setVisible(final @Nullable Boolean visible) {
    this.visible = visible;
  }

  public void setAlpha(final @Nullable Double alpha) {
    this.alpha = alpha;
  }

  public void setChildren(final @Nullable List<ViewHierarchyNode> children) {
    this.children = children;
  }

  @Override
  public void serialize(@NotNull JsonObjectWriter writer, @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    if (renderingSystem != null) {
      writer.name(JsonKeys.RENDERING_SYSTEM).value(renderingSystem);
    }
    if (type != null) {
      writer.name(JsonKeys.TYPE).value(type);
    }
    if (identifier != null) {
      writer.name(JsonKeys.IDENTIFIER).value(identifier);
    }
    if (tag != null) {
      writer.name(JsonKeys.TAG).value(tag);
    }
    if (width != null) {
      writer.name(JsonKeys.WIDTH).value(width);
    }
    if (height != null) {
      writer.name(JsonKeys.HEIGHT).value(height);
    }
    if (x != null) {
      writer.name(JsonKeys.X).value(x);
    }
    if (y != null) {
      writer.name(JsonKeys.Y).value(y);
    }
    if (visible != null) {
      writer.name(JsonKeys.VISIBLE).value(visible);
    }
    if (alpha != null) {
      writer.name(JsonKeys.ALPHA).value(alpha);
    }
    if (children != null && !children.isEmpty()) {
      writer.name(JsonKeys.CHILDREN).value(logger, children);
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

  public static final class Deserializer implements JsonDeserializer<ViewHierarchyNode> {

    @Override
    public @NotNull ViewHierarchyNode deserialize(
        @NotNull JsonObjectReader reader, @NotNull ILogger logger) throws Exception {
      @Nullable Map<String, Object> unknown = null;
      @NotNull final ViewHierarchyNode node = new ViewHierarchyNode();

      reader.beginObject();
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.RENDERING_SYSTEM:
            node.renderingSystem = reader.nextStringOrNull();
            break;
          case JsonKeys.TYPE:
            node.type = reader.nextStringOrNull();
            break;
          case JsonKeys.IDENTIFIER:
            node.identifier = reader.nextStringOrNull();
            break;
          case JsonKeys.TAG:
            node.tag = reader.nextStringOrNull();
            break;
          case JsonKeys.WIDTH:
            node.width = reader.nextDoubleOrNull();
            break;
          case JsonKeys.HEIGHT:
            node.height = reader.nextDoubleOrNull();
            break;
          case JsonKeys.X:
            node.x = reader.nextDoubleOrNull();
            break;
          case JsonKeys.Y:
            node.y = reader.nextDoubleOrNull();
            break;
          case JsonKeys.VISIBLE:
            node.visible = reader.nextBooleanOrNull();
            break;
          case JsonKeys.ALPHA:
            node.alpha = reader.nextDoubleOrNull();
            break;
          case JsonKeys.CHILDREN:
            node.children = reader.nextList(logger, this);
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

      node.setUnknown(unknown);
      return node;
    }
  }
}
