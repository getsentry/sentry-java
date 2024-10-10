package io.sentry;

import io.sentry.util.Objects;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class ProfilingTransactionData implements JsonUnknown, JsonSerializable {
  private @NotNull String id; // transaction event id (the current transaction_id)
  private @NotNull String traceId; // trace id (the current trace_id)
  private @NotNull String name; // transaction name
  private @NotNull Long relativeStartNs; // timestamp in nanoseconds this transaction started
  private @Nullable Long relativeEndNs; // timestamp in nanoseconds this transaction ended
  private @NotNull Long relativeStartCpuMs; // cpu time in milliseconds this transaction started
  private @Nullable Long relativeEndCpuMs; // cpu time in milliseconds this transaction ended

  private @Nullable Map<String, Object> unknown;

  public ProfilingTransactionData() {
    this(NoOpTransaction.getInstance(), 0L, 0L);
  }

  public ProfilingTransactionData(
      @NotNull ITransaction transaction, @NotNull Long startNs, @NotNull Long startCpuMs) {
    this.id = transaction.getEventId().toString();
    this.traceId = transaction.getSpanContext().getTraceId().toString();
    this.name = transaction.getName().isEmpty() ? "unknown" : transaction.getName();
    this.relativeStartNs = startNs;
    this.relativeStartCpuMs = startCpuMs;
  }

  /**
   * Notifies this transaction data that the transaction (or the profile) finished, to update its
   * internal values. It's safe to call this method multiple times
   *
   * @param endNs The timestamp in nanoseconds the transaction (or profile) finished.
   * @param profileStartNs The timestamp the profile started, so that timestamps can be converted in
   *     times relative to the profile start timestamp.
   */
  public void notifyFinish(
      @NotNull Long endNs,
      @NotNull Long profileStartNs,
      @NotNull Long endCpuMs,
      @NotNull Long profileStartCpuMs) {
    if (this.relativeEndNs == null) {
      this.relativeEndNs = endNs - profileStartNs;
      this.relativeStartNs = relativeStartNs - profileStartNs;
      this.relativeEndCpuMs = endCpuMs - profileStartCpuMs;
      this.relativeStartCpuMs = relativeStartCpuMs - profileStartCpuMs;
    }
  }

  public @NotNull String getId() {
    return id;
  }

  public @NotNull String getTraceId() {
    return traceId;
  }

  public @NotNull String getName() {
    return name;
  }

  public @NotNull Long getRelativeStartNs() {
    return relativeStartNs;
  }

  public @Nullable Long getRelativeEndNs() {
    return relativeEndNs;
  }

  public @Nullable Long getRelativeEndCpuMs() {
    return relativeEndCpuMs;
  }

  public @NotNull Long getRelativeStartCpuMs() {
    return relativeStartCpuMs;
  }

  public void setId(@NotNull String id) {
    this.id = id;
  }

  public void setTraceId(@NotNull String traceId) {
    this.traceId = traceId;
  }

  public void setName(@NotNull String name) {
    this.name = name;
  }

  public void setRelativeStartNs(@NotNull Long relativeStartNs) {
    this.relativeStartNs = relativeStartNs;
  }

  public void setRelativeEndNs(@Nullable Long relativeEndNs) {
    this.relativeEndNs = relativeEndNs;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ProfilingTransactionData that = (ProfilingTransactionData) o;
    return id.equals(that.id)
        && traceId.equals(that.traceId)
        && name.equals(that.name)
        && relativeStartNs.equals(that.relativeStartNs)
        && relativeStartCpuMs.equals(that.relativeStartCpuMs)
        && Objects.equals(relativeEndCpuMs, that.relativeEndCpuMs)
        && Objects.equals(relativeEndNs, that.relativeEndNs)
        && Objects.equals(unknown, that.unknown);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        id,
        traceId,
        name,
        relativeStartNs,
        relativeEndNs,
        relativeStartCpuMs,
        relativeEndCpuMs,
        unknown);
  }

  // JsonSerializable

  public static final class JsonKeys {
    public static final String ID = "id";
    public static final String TRACE_ID = "trace_id";
    public static final String NAME = "name";
    public static final String START_NS = "relative_start_ns";
    public static final String END_NS = "relative_end_ns";
    public static final String START_CPU_MS = "relative_cpu_start_ms";
    public static final String END_CPU_MS = "relative_cpu_end_ms";
  }

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    writer.name(JsonKeys.ID).value(logger, id);
    writer.name(JsonKeys.TRACE_ID).value(logger, traceId);
    writer.name(JsonKeys.NAME).value(logger, name);
    writer.name(JsonKeys.START_NS).value(logger, relativeStartNs);
    writer.name(JsonKeys.END_NS).value(logger, relativeEndNs);
    writer.name(JsonKeys.START_CPU_MS).value(logger, relativeStartCpuMs);
    writer.name(JsonKeys.END_CPU_MS).value(logger, relativeEndCpuMs);
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

  public static final class Deserializer implements JsonDeserializer<ProfilingTransactionData> {

    @Override
    public @NotNull ProfilingTransactionData deserialize(
        @NotNull ObjectReader reader, @NotNull ILogger logger) throws Exception {
      reader.beginObject();
      ProfilingTransactionData data = new ProfilingTransactionData();
      Map<String, Object> unknown = null;

      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.ID:
            String id = reader.nextStringOrNull();
            if (id != null) {
              data.id = id;
            }
            break;
          case JsonKeys.TRACE_ID:
            String traceId = reader.nextStringOrNull();
            if (traceId != null) {
              data.traceId = traceId;
            }
            break;
          case JsonKeys.NAME:
            String name = reader.nextStringOrNull();
            if (name != null) {
              data.name = name;
            }
            break;
          case JsonKeys.START_NS:
            Long startNs = reader.nextLongOrNull();
            if (startNs != null) {
              data.relativeStartNs = startNs;
            }
            break;
          case JsonKeys.END_NS:
            Long endNs = reader.nextLongOrNull();
            if (endNs != null) {
              data.relativeEndNs = endNs;
            }
            break;
          case JsonKeys.START_CPU_MS:
            Long startCpuMs = reader.nextLongOrNull();
            if (startCpuMs != null) {
              data.relativeStartCpuMs = startCpuMs;
            }
            break;
          case JsonKeys.END_CPU_MS:
            Long endCpuMs = reader.nextLongOrNull();
            if (endCpuMs != null) {
              data.relativeEndCpuMs = endCpuMs;
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
