package io.sentry;

import java.io.IOException;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SpanLink implements JsonSerializable {
  private @NotNull SpanContext context;

  private @Nullable Map<String, Object> attributes;

  public SpanLink(@NotNull SpanContext context, @Nullable Map<String, Object> attributes) {
    this.context = context;
    this.attributes = attributes;
  }

  public static final class JsonKeys {
    public static final String TRACE_ID = "trace_id";
    public static final String SPAN_ID = "span_id";
    public static final String SAMPLED = "sampled";
    public static final String ATTRIBUTES = "attributes";
  }

  @Override
  public void serialize(@NotNull ObjectWriter writer, @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    writer.name(JsonKeys.TRACE_ID);
    context.getTraceId().serialize(writer, logger);
    writer.name(JsonKeys.SPAN_ID);
    context.getSpanId().serialize(writer, logger);
    if (context.getSampled() != null) {
      writer.name(JsonKeys.SAMPLED);
      writer.value(context.getSampled());
    }
    if (attributes != null) {
      writer.name(JsonKeys.ATTRIBUTES).value(logger, attributes);
    }
    writer.endObject();
  }
}
