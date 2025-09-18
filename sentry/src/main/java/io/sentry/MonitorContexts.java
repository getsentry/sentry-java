package io.sentry;

import io.sentry.util.Objects;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MonitorContexts extends ConcurrentHashMap<String, Object>
    implements JsonSerializable {
  private static final long serialVersionUID = 3987329379811822556L;

  public MonitorContexts() {}

  public MonitorContexts(final @NotNull MonitorContexts contexts) {
    for (final Entry<String, Object> entry : contexts.entrySet()) {
      if (entry != null) {
        final Object value = entry.getValue();
        if (SpanContext.TYPE.equals(entry.getKey()) && value instanceof SpanContext) {
          this.setTrace(new SpanContext((SpanContext) value));
        } else {
          this.put(entry.getKey(), value);
        }
      }
    }
  }

  private @Nullable <T> T toContextType(final @NotNull String key, final @NotNull Class<T> clazz) {
    final Object item = get(key);
    return clazz.isInstance(item) ? clazz.cast(item) : null;
  }

  public @Nullable SpanContext getTrace() {
    return toContextType(SpanContext.TYPE, SpanContext.class);
  }

  public void setTrace(final @NotNull SpanContext traceContext) {
    Objects.requireNonNull(traceContext, "traceContext is required");
    this.put(SpanContext.TYPE, traceContext);
  }

  // region json

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    // Serialize in alphabetical order to keep determinism.
    final List<String> sortedKeys = Collections.list(keys());
    Collections.sort(sortedKeys);
    for (final String key : sortedKeys) {
      final Object value = get(key);
      if (value != null) {
        writer.name(key).value(logger, value);
      }
    }
    writer.endObject();
  }

  public static final class Deserializer implements JsonDeserializer<MonitorContexts> {

    @Override
    public @NotNull MonitorContexts deserialize(
        final @NotNull ObjectReader reader, final @NotNull ILogger logger) throws Exception {
      final MonitorContexts contexts = new MonitorContexts();
      reader.beginObject();
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case SpanContext.TYPE:
            contexts.setTrace(new SpanContext.Deserializer().deserialize(reader, logger));
            break;
          default:
            Object object = reader.nextObjectOrNull();
            if (object != null) {
              contexts.put(nextName, object);
            }
            break;
        }
      }
      reader.endObject();
      return contexts;
    }
  }

  // endregion
}
