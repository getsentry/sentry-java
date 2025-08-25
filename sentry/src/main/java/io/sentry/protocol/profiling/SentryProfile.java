package io.sentry.protocol.profiling;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonSerializable;
import io.sentry.JsonUnknown;
import io.sentry.ObjectReader;
import io.sentry.ObjectWriter;
import io.sentry.protocol.SentryStackFrame;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SentryProfile implements JsonUnknown, JsonSerializable {
  public @Nullable List<JfrSample> samples;

  public @Nullable List<List<Integer>> stacks; // List of frame indices

  public @Nullable List<SentryStackFrame> frames;

  public @Nullable Map<String, ThreadMetadata> threadMetadata; // Key is Thread ID (String)

  private @Nullable Map<String, Object> unknown;

  @Override
  public void serialize(@NotNull ObjectWriter writer, @NotNull ILogger logger) throws IOException {
    writer.beginObject();
    if (samples != null) {
      writer.name(JsonKeys.SAMPLES).value(logger, samples);
    }
    if (stacks != null) {
      writer.name(JsonKeys.STACKS).value(logger, stacks);
    }
    if (frames != null) {
      writer.name(JsonKeys.FRAMES).value(logger, frames);
    }

    if (threadMetadata != null) {
      writer.name(JsonKeys.THREAD_METADATA).value(logger, threadMetadata);
      //      writer.beginObject();
      //      for (String key : threadMetadata.keySet()) {
      //        ThreadMetadata value = threadMetadata.get(key);
      //        writer.name(key).value(logger, value);
      //      }
      //      writer.endObject();
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

  public static final class JsonKeys {
    public static final String SAMPLES = "samples";
    public static final String STACKS = "stacks";
    public static final String FRAMES = "frames";
    public static final String THREAD_METADATA = "thread_metadata";
  }

  public static final class Deserializer implements JsonDeserializer<SentryProfile> {

    @Override
    public @NotNull SentryProfile deserialize(@NotNull ObjectReader reader, @NotNull ILogger logger)
        throws Exception {
      reader.beginObject();
      SentryProfile data = new SentryProfile();
      return data;
      //      Map<String, Object> unknown = null;
      //
      //      while (reader.peek() == JsonToken.NAME) {
      //        final String nextName = reader.nextName();
      //        switch (nextName) {
      //          case JsonKeys.FRAMES:
      //            List<JfrFrame> jfrFrame = reader.nextListOrNull(logger, new
      // JfrFrame().Deserializer());
      //            if (jfrFrame != null) {
      //              data.frames = jfrFrame;
      //            }
      //            break;
      //          case JsonKeys.SAMPLES:
      //            List<JfrSample> jfrSamples = reader.nextListOrNull(logger, new
      // JfrSample().Deserializer());
      //            if (jfrSamples != null) {
      //              data.samples = jfrSamples;
      //            }
      //            break;
      //
      ////          case JsonKeys.STACKS:
      ////            List<List<Integer>> jfrStacks = reader.nextListOrNull(logger);
      ////            if (jfrSamples != null) {
      ////              data.samples = jfrSamples;
      ////            }
      ////            break;
      //
      //          default:
      //            if (unknown == null) {
      //              unknown = new ConcurrentHashMap<>();
      //            }
      //            reader.nextUnknown(logger, unknown, nextName);
      //            break;
      //        }
      //      }
      //      data.setUnknown(unknown);
      //      reader.endObject();
      //      return data;
    }
  }
}
