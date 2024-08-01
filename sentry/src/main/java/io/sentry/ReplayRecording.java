package io.sentry;

import io.sentry.rrweb.RRWebBreadcrumbEvent;
import io.sentry.rrweb.RRWebEvent;
import io.sentry.rrweb.RRWebEventType;
import io.sentry.rrweb.RRWebIncrementalSnapshotEvent;
import io.sentry.rrweb.RRWebInteractionEvent;
import io.sentry.rrweb.RRWebInteractionMoveEvent;
import io.sentry.rrweb.RRWebMetaEvent;
import io.sentry.rrweb.RRWebSpanEvent;
import io.sentry.rrweb.RRWebVideoEvent;
import io.sentry.util.MapObjectReader;
import io.sentry.util.Objects;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ReplayRecording implements JsonUnknown, JsonSerializable {

  public static final class JsonKeys {
    public static final String SEGMENT_ID = "segment_id";
  }

  private @Nullable Integer segmentId;
  private @Nullable List<? extends RRWebEvent> payload;
  private @Nullable Map<String, Object> unknown;

  @Nullable
  public Integer getSegmentId() {
    return segmentId;
  }

  public void setSegmentId(final @Nullable Integer segmentId) {
    this.segmentId = segmentId;
  }

  @Nullable
  public List<? extends RRWebEvent> getPayload() {
    return payload;
  }

  public void setPayload(final @Nullable List<? extends RRWebEvent> payload) {
    this.payload = payload;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ReplayRecording that = (ReplayRecording) o;
    return Objects.equals(segmentId, that.segmentId) && Objects.equals(payload, that.payload);
  }

  @Override
  public int hashCode() {
    return Objects.hash(segmentId, payload);
  }

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    if (segmentId != null) {
      writer.name(JsonKeys.SEGMENT_ID).value(segmentId);
    }

    if (unknown != null) {
      for (String key : unknown.keySet()) {
        Object value = unknown.get(key);
        writer.name(key).value(logger, value);
      }
    }
    writer.endObject();

    // {"segment_id":0}\n{json-serialized-rrweb-protocol}

    writer.setLenient(true);
    if (segmentId != null) {
      writer.jsonValue("\n");
    }
    if (payload != null) {
      writer.value(logger, payload);
    }
    writer.setLenient(false);
  }

  @Override
  public @Nullable Map<String, Object> getUnknown() {
    return unknown;
  }

  @Override
  public void setUnknown(@Nullable Map<String, Object> unknown) {
    this.unknown = unknown;
  }

  public static final class Deserializer implements JsonDeserializer<ReplayRecording> {

    @SuppressWarnings("unchecked")
    @Override
    public @NotNull ReplayRecording deserialize(
        @NotNull ObjectReader reader, @NotNull ILogger logger) throws Exception {

      final ReplayRecording replay = new ReplayRecording();

      @Nullable Map<String, Object> unknown = null;
      @Nullable Integer segmentId = null;
      @Nullable List<RRWebEvent> payload = null;

      reader.beginObject();
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.SEGMENT_ID:
            segmentId = reader.nextIntegerOrNull();
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

      // {"segment_id":0}\n{json-serialized-rrweb-protocol}

      reader.setLenient(true);
      List<Object> events = (List<Object>) reader.nextObjectOrNull();
      reader.setLenient(false);

      // since we lose the type of an rrweb event at runtime, we have to recover it from a map
      if (events != null) {
        payload = new ArrayList<>(events.size());
        for (Object event : events) {
          if (event instanceof Map) {
            final Map<String, Object> eventMap = (Map<String, Object>) event;
            final ObjectReader mapReader = new MapObjectReader(eventMap);
            for (final Map.Entry<String, Object> entry : eventMap.entrySet()) {
              final String key = entry.getKey();
              final Object value = entry.getValue();
              if (key.equals(RRWebEvent.JsonKeys.TYPE)) {
                final RRWebEventType type = RRWebEventType.values()[(int) value];
                switch (type) {
                  case IncrementalSnapshot:
                    @Nullable
                    Map<String, Object> incrementalData =
                        (Map<String, Object>) eventMap.get("data");
                    if (incrementalData == null) {
                      incrementalData = Collections.emptyMap();
                    }
                    final Integer sourceInt =
                        (Integer)
                            incrementalData.get(RRWebIncrementalSnapshotEvent.JsonKeys.SOURCE);
                    if (sourceInt != null) {
                      final RRWebIncrementalSnapshotEvent.IncrementalSource source =
                          RRWebIncrementalSnapshotEvent.IncrementalSource.values()[sourceInt];
                      switch (source) {
                        case MouseInteraction:
                          final RRWebInteractionEvent interactionEvent =
                              new RRWebInteractionEvent.Deserializer()
                                  .deserialize(mapReader, logger);
                          payload.add(interactionEvent);
                          break;
                        case TouchMove:
                          final RRWebInteractionMoveEvent interactionMoveEvent =
                              new RRWebInteractionMoveEvent.Deserializer()
                                  .deserialize(mapReader, logger);
                          payload.add(interactionMoveEvent);
                          break;
                        default:
                          logger.log(
                              SentryLevel.DEBUG,
                              "Unsupported rrweb incremental snapshot type %s",
                              source);
                          break;
                      }
                    }
                    break;
                  case Meta:
                    final RRWebEvent metaEvent =
                        new RRWebMetaEvent.Deserializer().deserialize(mapReader, logger);
                    payload.add(metaEvent);
                    break;
                  case Custom:
                    @Nullable
                    Map<String, Object> customData = (Map<String, Object>) eventMap.get("data");
                    if (customData == null) {
                      customData = Collections.emptyMap();
                    }
                    final String tag = (String) customData.get(RRWebEvent.JsonKeys.TAG);
                    if (tag != null) {
                      switch (tag) {
                        case RRWebVideoEvent.EVENT_TAG:
                          final RRWebEvent videoEvent =
                              new RRWebVideoEvent.Deserializer().deserialize(mapReader, logger);
                          payload.add(videoEvent);
                          break;
                        case RRWebBreadcrumbEvent.EVENT_TAG:
                          final RRWebEvent breadcrumbEvent =
                              new RRWebBreadcrumbEvent.Deserializer()
                                  .deserialize(mapReader, logger);
                          payload.add(breadcrumbEvent);
                          break;
                        case RRWebSpanEvent.EVENT_TAG:
                          final RRWebEvent spanEvent =
                              new RRWebSpanEvent.Deserializer().deserialize(mapReader, logger);
                          payload.add(spanEvent);
                          break;
                        default:
                          logger.log(SentryLevel.DEBUG, "Unsupported rrweb event type %s", type);
                          break;
                      }
                    }
                    break;
                  default:
                    logger.log(SentryLevel.DEBUG, "Unsupported rrweb event type %s", type);
                    break;
                }
              }
            }
          }
        }
      }

      replay.setSegmentId(segmentId);
      replay.setPayload(payload);
      replay.setUnknown(unknown);
      return replay;
    }
  }
}
