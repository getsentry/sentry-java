package io.sentry.protocol.profiling;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonSerializable;
import io.sentry.JsonUnknown;
import io.sentry.ObjectReader;
import io.sentry.ObjectWriter;
import io.sentry.protocol.SentryStackFrame;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SentryProfile implements JsonUnknown, JsonSerializable {
  private @NotNull List<SentrySample> samples = new ArrayList<>();

  private @NotNull List<List<Integer>> stacks = new ArrayList<>(); // List of frame indices

  private @NotNull List<SentryStackFrame> frames = new ArrayList<>(); // List of stack frames

  private @NotNull Map<String, SentryThreadMetadata> threadMetadata =
      new HashMap<>(); // Key is Thread ID (String)

  private @Nullable Map<String, Object> unknown;

  @Override
  public void serialize(@NotNull ObjectWriter writer, @NotNull ILogger logger) throws IOException {
    writer.beginObject();
    writer.name(JsonKeys.SAMPLES).value(logger, samples);
    writer.name(JsonKeys.STACKS).value(logger, stacks);
    writer.name(JsonKeys.FRAMES).value(logger, frames);
    writer.name(JsonKeys.THREAD_METADATA).value(logger, threadMetadata);

    if (unknown != null) {
      for (String key : unknown.keySet()) {
        Object value = unknown.get(key);
        writer.name(key).value(logger, value);
      }
    }
    writer.endObject();
  }

  public @NotNull List<SentrySample> getSamples() {
    return samples;
  }

  public void setSamples(@NotNull List<SentrySample> samples) {
    this.samples = samples;
  }

  public @NotNull List<List<Integer>> getStacks() {
    return stacks;
  }

  public void setStacks(@NotNull List<List<Integer>> stacks) {
    this.stacks = stacks;
  }

  public @NotNull List<SentryStackFrame> getFrames() {
    return frames;
  }

  public void setFrames(@NotNull List<SentryStackFrame> frames) {
    this.frames = frames;
  }

  public @NotNull Map<String, SentryThreadMetadata> getThreadMetadata() {
    return threadMetadata;
  }

  public void setThreadMetadata(@NotNull Map<String, SentryThreadMetadata> threadMetadata) {
    this.threadMetadata = threadMetadata;
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
      Map<String, Object> unknown = null;

      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.FRAMES:
            List<SentryStackFrame> jfrFrame =
                reader.nextListOrNull(logger, new SentryStackFrame.Deserializer());
            if (jfrFrame != null) {
              data.frames = jfrFrame;
            }
            break;
          case JsonKeys.SAMPLES:
            List<SentrySample> sentrySamples =
                reader.nextListOrNull(logger, new SentrySample.Deserializer());
            if (sentrySamples != null) {
              data.samples = sentrySamples;
            }
            break;
          case JsonKeys.THREAD_METADATA:
            Map<String, SentryThreadMetadata> threadMetadata =
                reader.nextMapOrNull(logger, new SentryThreadMetadata.Deserializer());
            if (threadMetadata != null) {
              data.threadMetadata = threadMetadata;
            }
            break;
          case JsonKeys.STACKS:
            List<List<Integer>> jfrStacks =
                reader.nextOrNull(logger, new NestedIntegerListDeserializer());
            if (jfrStacks != null) {
              data.stacks = jfrStacks;
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

  // Custom Deserializer to handle nested Integer list
  private static final class NestedIntegerListDeserializer
      implements JsonDeserializer<List<List<Integer>>> {
    @Override
    public @NotNull List<List<Integer>> deserialize(
        @NotNull ObjectReader reader, @NotNull ILogger logger) throws Exception {
      List<List<Integer>> result = new ArrayList<>();
      reader.beginArray();
      while (reader.hasNext()) {
        List<Integer> innerList = new ArrayList<>();
        reader.beginArray();
        while (reader.hasNext()) {
          innerList.add(reader.nextInt());
        }
        reader.endArray();
        result.add(innerList);
      }
      reader.endArray();
      return result;
    }
  }
}
