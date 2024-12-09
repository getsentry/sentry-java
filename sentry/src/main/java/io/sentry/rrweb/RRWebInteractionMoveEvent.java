package io.sentry.rrweb;

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

@SuppressWarnings("SameNameButDifferent")
public final class RRWebInteractionMoveEvent extends RRWebIncrementalSnapshotEvent
    implements JsonSerializable, JsonUnknown {

  public static final class Position implements JsonSerializable, JsonUnknown {

    private int id;

    private float x;

    private float y;

    private long timeOffset;

    private @Nullable Map<String, Object> unknown;

    public int getId() {
      return id;
    }

    public void setId(final int id) {
      this.id = id;
    }

    public float getX() {
      return x;
    }

    public void setX(final float x) {
      this.x = x;
    }

    public float getY() {
      return y;
    }

    public void setY(final float y) {
      this.y = y;
    }

    public long getTimeOffset() {
      return timeOffset;
    }

    public void setTimeOffset(final long timeOffset) {
      this.timeOffset = timeOffset;
    }

    @Override
    public @Nullable Map<String, Object> getUnknown() {
      return unknown;
    }

    @Override
    public void setUnknown(final @Nullable Map<String, Object> unknown) {
      this.unknown = unknown;
    }

    // region json

    // rrweb uses camelCase hence the json keys are in camelCase here
    public static final class JsonKeys {
      public static final String ID = "id";
      public static final String X = "x";
      public static final String Y = "y";
      public static final String TIME_OFFSET = "timeOffset";
    }

    @Override
    public void serialize(@NotNull ObjectWriter writer, @NotNull ILogger logger)
        throws IOException {
      writer.beginObject();
      writer.name(JsonKeys.ID).value(id);
      writer.name(JsonKeys.X).value(x);
      writer.name(JsonKeys.Y).value(y);
      writer.name(JsonKeys.TIME_OFFSET).value(timeOffset);
      if (unknown != null) {
        for (final String key : unknown.keySet()) {
          final Object value = unknown.get(key);
          writer.name(key);
          writer.value(logger, value);
        }
      }
      writer.endObject();
    }

    public static final class Deserializer implements JsonDeserializer<Position> {

      @Override
      public @NotNull Position deserialize(@NotNull ObjectReader reader, @NotNull ILogger logger)
          throws Exception {
        reader.beginObject();
        @Nullable Map<String, Object> unknown = null;

        final Position position = new Position();

        while (reader.peek() == JsonToken.NAME) {
          final String nextName = reader.nextName();
          switch (nextName) {
            case JsonKeys.ID:
              position.id = reader.nextInt();
              break;
            case JsonKeys.X:
              position.x = reader.nextFloat();
              break;
            case JsonKeys.Y:
              position.y = reader.nextFloat();
              break;
            case JsonKeys.TIME_OFFSET:
              position.timeOffset = reader.nextLong();
              break;
            default:
              if (unknown == null) {
                unknown = new HashMap<>();
              }
              reader.nextUnknown(logger, unknown, nextName);
              break;
          }
        }

        position.setUnknown(unknown);
        reader.endObject();
        return position;
      }
    }
    // endregion json
  }

  private int pointerId;
  private @Nullable List<Position> positions;
  // to support unknown json attributes with nesting, we have to have unknown map for each of the
  // nested object in json: { ..., "data": { ... } }
  private @Nullable Map<String, Object> unknown;
  private @Nullable Map<String, Object> dataUnknown;

  public RRWebInteractionMoveEvent() {
    super(IncrementalSource.TouchMove);
  }

  @Nullable
  public Map<String, Object> getDataUnknown() {
    return dataUnknown;
  }

  public void setDataUnknown(final @Nullable Map<String, Object> dataUnknown) {
    this.dataUnknown = dataUnknown;
  }

  @Override
  public @Nullable Map<String, Object> getUnknown() {
    return unknown;
  }

  @Override
  public void setUnknown(final @Nullable Map<String, Object> unknown) {
    this.unknown = unknown;
  }

  @Nullable
  public List<Position> getPositions() {
    return positions;
  }

  public void setPositions(final @Nullable List<Position> positions) {
    this.positions = positions;
  }

  public int getPointerId() {
    return pointerId;
  }

  public void setPointerId(final int pointerId) {
    this.pointerId = pointerId;
  }

  // region json

  // rrweb uses camelCase hence the json keys are in camelCase here
  public static final class JsonKeys {
    public static final String DATA = "data";
    public static final String POSITIONS = "positions";
    public static final String POINTER_ID = "pointerId";
  }

  @Override
  public void serialize(@NotNull ObjectWriter writer, @NotNull ILogger logger) throws IOException {
    writer.beginObject();
    new RRWebEvent.Serializer().serialize(this, writer, logger);
    writer.name(JsonKeys.DATA);
    serializeData(writer, logger);
    if (unknown != null) {
      for (final String key : unknown.keySet()) {
        final Object value = unknown.get(key);
        writer.name(key);
        writer.value(logger, value);
      }
    }
    writer.endObject();
  }

  private void serializeData(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    new RRWebIncrementalSnapshotEvent.Serializer().serialize(this, writer, logger);
    if (positions != null && !positions.isEmpty()) {
      writer.name(JsonKeys.POSITIONS).value(logger, positions);
    }
    writer.name(JsonKeys.POINTER_ID).value(pointerId);
    if (dataUnknown != null) {
      for (String key : dataUnknown.keySet()) {
        Object value = dataUnknown.get(key);
        writer.name(key);
        writer.value(logger, value);
      }
    }
    writer.endObject();
  }

  public static final class Deserializer implements JsonDeserializer<RRWebInteractionMoveEvent> {

    @Override
    public @NotNull RRWebInteractionMoveEvent deserialize(
        @NotNull ObjectReader reader, @NotNull ILogger logger) throws Exception {
      reader.beginObject();
      @Nullable Map<String, Object> unknown = null;

      final RRWebInteractionMoveEvent event = new RRWebInteractionMoveEvent();
      final RRWebEvent.Deserializer baseEventDeserializer = new RRWebEvent.Deserializer();

      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.DATA:
            deserializeData(event, reader, logger);
            break;
          default:
            if (!baseEventDeserializer.deserializeValue(event, nextName, reader, logger)) {
              if (unknown == null) {
                unknown = new HashMap<>();
              }
              reader.nextUnknown(logger, unknown, nextName);
            }
            break;
        }
      }

      event.setUnknown(unknown);
      reader.endObject();
      return event;
    }

    private void deserializeData(
        final @NotNull RRWebInteractionMoveEvent event,
        final @NotNull ObjectReader reader,
        final @NotNull ILogger logger)
        throws Exception {
      @Nullable Map<String, Object> dataUnknown = null;

      final RRWebIncrementalSnapshotEvent.Deserializer baseEventDeserializer =
          new RRWebIncrementalSnapshotEvent.Deserializer();

      reader.beginObject();
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.POSITIONS:
            event.positions = reader.nextListOrNull(logger, new Position.Deserializer());
            break;
          case JsonKeys.POINTER_ID:
            event.pointerId = reader.nextInt();
            break;
          default:
            if (!baseEventDeserializer.deserializeValue(event, nextName, reader, logger)) {
              if (dataUnknown == null) {
                dataUnknown = new HashMap<>();
              }
              reader.nextUnknown(logger, dataUnknown, nextName);
            }
            break;
        }
      }
      event.setDataUnknown(dataUnknown);
      reader.endObject();
    }
  }
  // endregion json
}
