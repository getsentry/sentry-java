package io.sentry.protocol;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonSerializable;
import io.sentry.JsonUnknown;
import io.sentry.ObjectReader;
import io.sentry.ObjectWriter;
import io.sentry.SentryLevel;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** An independent call tree of an {@link AnrFlameGraph}, usually one per sampled thread. */
@ApiStatus.Internal
public final class AnrFlameGraphTree implements JsonUnknown, JsonSerializable {

  public static final class JsonKeys {
    public static final String ROOTS = "roots";
    public static final String THREAD_ID = "thread_id";
    public static final String THREAD_ATTRIBUTED = "thread_attributed";
  }

  private final @NotNull List<Integer> roots;
  private @Nullable String threadId;
  private @Nullable Boolean threadAttributed;
  private @Nullable Map<String, Object> unknown;

  public AnrFlameGraphTree(final @NotNull List<Integer> roots) {
    this.roots = roots;
  }

  public @NotNull List<Integer> getRoots() {
    return roots;
  }

  public @Nullable String getThreadId() {
    return threadId;
  }

  public void setThreadId(final @Nullable String threadId) {
    this.threadId = threadId;
  }

  public @Nullable Boolean getThreadAttributed() {
    return threadAttributed;
  }

  public void setThreadAttributed(final @Nullable Boolean threadAttributed) {
    this.threadAttributed = threadAttributed;
  }

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    writer.name(JsonKeys.ROOTS).value(logger, roots);
    if (threadId != null) {
      writer.name(JsonKeys.THREAD_ID).value(threadId);
    }
    if (threadAttributed != null) {
      writer.name(JsonKeys.THREAD_ATTRIBUTED).value(threadAttributed);
    }
    if (unknown != null) {
      for (final String key : unknown.keySet()) {
        writer.name(key).value(logger, unknown.get(key));
      }
    }
    writer.endObject();
  }

  @Override
  public @Nullable Map<String, Object> getUnknown() {
    return unknown;
  }

  @Override
  public void setUnknown(final @Nullable Map<String, Object> unknown) {
    this.unknown = unknown;
  }

  public static final class Deserializer implements JsonDeserializer<AnrFlameGraphTree> {

    @Override
    public @NotNull AnrFlameGraphTree deserialize(
        final @NotNull ObjectReader reader, final @NotNull ILogger logger) throws Exception {
      @Nullable List<Integer> roots = null;
      @Nullable String threadId = null;
      @Nullable Boolean threadAttributed = null;
      @Nullable Map<String, Object> unknown = null;

      reader.beginObject();
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.ROOTS:
            roots = reader.nextOrNull(logger, new AnrFlameGraphNode.IntegerListDeserializer());
            break;
          case JsonKeys.THREAD_ID:
            threadId = reader.nextStringOrNull();
            break;
          case JsonKeys.THREAD_ATTRIBUTED:
            threadAttributed = reader.nextBooleanOrNull();
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

      if (roots == null) {
        final String message = "Missing required field \"" + JsonKeys.ROOTS + "\"";
        final Exception exception = new IllegalStateException(message);
        logger.log(SentryLevel.ERROR, message, exception);
        throw exception;
      }

      final AnrFlameGraphTree tree = new AnrFlameGraphTree(roots);
      tree.setThreadId(threadId);
      tree.setThreadAttributed(threadAttributed);
      tree.setUnknown(unknown);
      return tree;
    }
  }
}
