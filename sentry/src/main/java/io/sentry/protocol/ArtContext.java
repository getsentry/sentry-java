package io.sentry.protocol;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonSerializable;
import io.sentry.JsonUnknown;
import io.sentry.ObjectReader;
import io.sentry.ObjectWriter;
import io.sentry.util.CollectionUtils;
import io.sentry.util.Objects;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Context containing ART (Android Runtime) specific information. This is only relevant for Android
 * and may be null on other platforms.
 */
public final class ArtContext implements JsonUnknown, JsonSerializable {
  public static final String TYPE = "art";

  private @Nullable Long gcTotalCount;
  private @Nullable Double gcTotalTime;
  private @Nullable Long gcBlockingCount;
  private @Nullable Double gcBlockingTime;
  private @Nullable Long gcPreOomeCount;
  private @Nullable Double gcWaitingTime;
  private @Nullable Long freeMemory;
  private @Nullable Long freeMemoryUntilGc;
  private @Nullable Long freeMemoryUntilOome;
  private @Nullable Long totalMemory;
  private @Nullable Long maxMemory;

  @SuppressWarnings("unused")
  private @Nullable Map<String, @NotNull Object> unknown;

  public ArtContext() {}

  ArtContext(final @NotNull ArtContext other) {
    this.gcTotalCount = other.gcTotalCount;
    this.gcTotalTime = other.gcTotalTime;
    this.gcBlockingCount = other.gcBlockingCount;
    this.gcBlockingTime = other.gcBlockingTime;
    this.gcPreOomeCount = other.gcPreOomeCount;
    this.gcWaitingTime = other.gcWaitingTime;
    this.freeMemory = other.freeMemory;
    this.freeMemoryUntilGc = other.freeMemoryUntilGc;
    this.freeMemoryUntilOome = other.freeMemoryUntilOome;
    this.totalMemory = other.totalMemory;
    this.maxMemory = other.maxMemory;
    this.unknown = CollectionUtils.newConcurrentHashMap(other.unknown);
  }

  /** Total number of GC collections since process start. */
  public @Nullable Long getGcTotalCount() {
    return gcTotalCount;
  }

  /** Total number of GC collections since process start. */
  public void setGcTotalCount(final @Nullable Long gcTotalCount) {
    this.gcTotalCount = gcTotalCount;
  }

  /** Total time spent in GC since process start, in milliseconds. */
  public @Nullable Double getGcTotalTime() {
    return gcTotalTime;
  }

  /** Total time spent in GC since process start, in milliseconds. */
  public void setGcTotalTime(final @Nullable Double gcTotalTime) {
    this.gcTotalTime = gcTotalTime;
  }

  /** Total number of blocking (stop-the-world) GC collections since process start. */
  public @Nullable Long getGcBlockingCount() {
    return gcBlockingCount;
  }

  /** Total number of blocking (stop-the-world) GC collections since process start. */
  public void setGcBlockingCount(final @Nullable Long gcBlockingCount) {
    this.gcBlockingCount = gcBlockingCount;
  }

  /** Total time spent in blocking (stop-the-world) GC since process start, in milliseconds. */
  public @Nullable Double getGcBlockingTime() {
    return gcBlockingTime;
  }

  /** Total time spent in blocking (stop-the-world) GC since process start, in milliseconds. */
  public void setGcBlockingTime(final @Nullable Double gcBlockingTime) {
    this.gcBlockingTime = gcBlockingTime;
  }

  /** Total number of GC collections triggered to prevent an OutOfMemoryError. */
  public @Nullable Long getGcPreOomeCount() {
    return gcPreOomeCount;
  }

  /** Total number of GC collections triggered to prevent an OutOfMemoryError. */
  public void setGcPreOomeCount(final @Nullable Long gcPreOomeCount) {
    this.gcPreOomeCount = gcPreOomeCount;
  }

  /** Total time threads spent waiting for GC to complete, in milliseconds. */
  public @Nullable Double getGcWaitingTime() {
    return gcWaitingTime;
  }

  /** Total time threads spent waiting for GC to complete, in milliseconds. */
  public void setGcWaitingTime(final @Nullable Double gcWaitingTime) {
    this.gcWaitingTime = gcWaitingTime;
  }

  /** Free memory available in the managed heap, in bytes. */
  public @Nullable Long getFreeMemory() {
    return freeMemory;
  }

  /** Free memory available in the managed heap, in bytes. */
  public void setFreeMemory(final @Nullable Long freeMemory) {
    this.freeMemory = freeMemory;
  }

  /** Free memory available until the next GC is triggered, in bytes. */
  public @Nullable Long getFreeMemoryUntilGc() {
    return freeMemoryUntilGc;
  }

  /** Free memory available until the next GC is triggered, in bytes. */
  public void setFreeMemoryUntilGc(final @Nullable Long freeMemoryUntilGc) {
    this.freeMemoryUntilGc = freeMemoryUntilGc;
  }

  /** Free memory available until an OutOfMemoryError is thrown, in bytes. */
  public @Nullable Long getFreeMemoryUntilOome() {
    return freeMemoryUntilOome;
  }

  /** Free memory available until an OutOfMemoryError is thrown, in bytes. */
  public void setFreeMemoryUntilOome(final @Nullable Long freeMemoryUntilOome) {
    this.freeMemoryUntilOome = freeMemoryUntilOome;
  }

  /** Total memory currently allocated for the managed heap, in bytes. */
  public @Nullable Long getTotalMemory() {
    return totalMemory;
  }

  /** Total memory currently allocated for the managed heap, in bytes. */
  public void setTotalMemory(final @Nullable Long totalMemory) {
    this.totalMemory = totalMemory;
  }

  /** Maximum memory the managed heap is allowed to grow to, in bytes. */
  public @Nullable Long getMaxMemory() {
    return maxMemory;
  }

  /** Maximum memory the managed heap is allowed to grow to, in bytes. */
  public void setMaxMemory(final @Nullable Long maxMemory) {
    this.maxMemory = maxMemory;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ArtContext that = (ArtContext) o;
    return Objects.equals(gcTotalCount, that.gcTotalCount)
        && Objects.equals(gcTotalTime, that.gcTotalTime)
        && Objects.equals(gcBlockingCount, that.gcBlockingCount)
        && Objects.equals(gcBlockingTime, that.gcBlockingTime)
        && Objects.equals(gcPreOomeCount, that.gcPreOomeCount)
        && Objects.equals(gcWaitingTime, that.gcWaitingTime)
        && Objects.equals(freeMemory, that.freeMemory)
        && Objects.equals(freeMemoryUntilGc, that.freeMemoryUntilGc)
        && Objects.equals(freeMemoryUntilOome, that.freeMemoryUntilOome)
        && Objects.equals(totalMemory, that.totalMemory)
        && Objects.equals(maxMemory, that.maxMemory);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        gcTotalCount,
        gcTotalTime,
        gcBlockingCount,
        gcBlockingTime,
        gcPreOomeCount,
        gcWaitingTime,
        freeMemory,
        freeMemoryUntilGc,
        freeMemoryUntilOome,
        totalMemory,
        maxMemory);
  }

  // region JsonSerializable

  public static final class JsonKeys {
    public static final String GC_TOTAL_COUNT = "gc_total_count";
    public static final String GC_TOTAL_TIME = "gc_total_time";
    public static final String GC_BLOCKING_COUNT = "gc_blocking_count";
    public static final String GC_BLOCKING_TIME = "gc_blocking_time";
    public static final String GC_PRE_OOME_COUNT = "gc_pre_oome_count";
    public static final String GC_WAITING_TIME = "gc_waiting_time";
    public static final String FREE_MEMORY = "free_memory";
    public static final String FREE_MEMORY_UNTIL_GC = "free_memory_until_gc";
    public static final String FREE_MEMORY_UNTIL_OOME = "free_memory_until_oome";
    public static final String TOTAL_MEMORY = "total_memory";
    public static final String MAX_MEMORY = "max_memory";
  }

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    if (gcTotalCount != null) {
      writer.name(JsonKeys.GC_TOTAL_COUNT).value(gcTotalCount);
    }
    if (gcTotalTime != null) {
      writer.name(JsonKeys.GC_TOTAL_TIME).value(gcTotalTime);
    }
    if (gcBlockingCount != null) {
      writer.name(JsonKeys.GC_BLOCKING_COUNT).value(gcBlockingCount);
    }
    if (gcBlockingTime != null) {
      writer.name(JsonKeys.GC_BLOCKING_TIME).value(gcBlockingTime);
    }
    if (gcPreOomeCount != null) {
      writer.name(JsonKeys.GC_PRE_OOME_COUNT).value(gcPreOomeCount);
    }
    if (gcWaitingTime != null) {
      writer.name(JsonKeys.GC_WAITING_TIME).value(gcWaitingTime);
    }
    if (freeMemory != null) {
      writer.name(JsonKeys.FREE_MEMORY).value(freeMemory);
    }
    if (freeMemoryUntilGc != null) {
      writer.name(JsonKeys.FREE_MEMORY_UNTIL_GC).value(freeMemoryUntilGc);
    }
    if (freeMemoryUntilOome != null) {
      writer.name(JsonKeys.FREE_MEMORY_UNTIL_OOME).value(freeMemoryUntilOome);
    }
    if (totalMemory != null) {
      writer.name(JsonKeys.TOTAL_MEMORY).value(totalMemory);
    }
    if (maxMemory != null) {
      writer.name(JsonKeys.MAX_MEMORY).value(maxMemory);
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

  @Nullable
  @Override
  public Map<String, Object> getUnknown() {
    return unknown;
  }

  @Override
  public void setUnknown(@Nullable Map<String, Object> unknown) {
    this.unknown = unknown;
  }

  public static final class Deserializer implements JsonDeserializer<ArtContext> {
    @Override
    public @NotNull ArtContext deserialize(@NotNull ObjectReader reader, @NotNull ILogger logger)
        throws Exception {
      reader.beginObject();
      ArtContext artContext = new ArtContext();
      Map<String, Object> unknown = null;
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.GC_TOTAL_COUNT:
            artContext.gcTotalCount = reader.nextLongOrNull();
            break;
          case JsonKeys.GC_TOTAL_TIME:
            artContext.gcTotalTime = reader.nextDoubleOrNull();
            break;
          case JsonKeys.GC_BLOCKING_COUNT:
            artContext.gcBlockingCount = reader.nextLongOrNull();
            break;
          case JsonKeys.GC_BLOCKING_TIME:
            artContext.gcBlockingTime = reader.nextDoubleOrNull();
            break;
          case JsonKeys.GC_PRE_OOME_COUNT:
            artContext.gcPreOomeCount = reader.nextLongOrNull();
            break;
          case JsonKeys.GC_WAITING_TIME:
            artContext.gcWaitingTime = reader.nextDoubleOrNull();
            break;
          case JsonKeys.FREE_MEMORY:
            artContext.freeMemory = reader.nextLongOrNull();
            break;
          case JsonKeys.FREE_MEMORY_UNTIL_GC:
            artContext.freeMemoryUntilGc = reader.nextLongOrNull();
            break;
          case JsonKeys.FREE_MEMORY_UNTIL_OOME:
            artContext.freeMemoryUntilOome = reader.nextLongOrNull();
            break;
          case JsonKeys.TOTAL_MEMORY:
            artContext.totalMemory = reader.nextLongOrNull();
            break;
          case JsonKeys.MAX_MEMORY:
            artContext.maxMemory = reader.nextLongOrNull();
            break;
          default:
            if (unknown == null) {
              unknown = new ConcurrentHashMap<>();
            }
            reader.nextUnknown(logger, unknown, nextName);
            break;
        }
      }
      artContext.setUnknown(unknown);
      reader.endObject();
      return artContext;
    }
  }
}
