package io.sentry.adapters;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import io.sentry.Span;

import java.lang.reflect.Type;

public final class SpanSerializerAdapter implements JsonSerializer<Span> {
  @Override
  public JsonElement serialize(Span src, Type typeOfSrc, JsonSerializationContext context) {
    if (src == null) {
      return null;
    }

    final JsonObject object = new JsonObject();
    object.add("trace_id", context.serialize(src.getTraceId()));
    object.add("span_id", context.serialize(src.getSpanId()));
    object.add("parent_span_id", context.serialize(src.getParentSpanId()));
    object.add("op", context.serialize(src.getOperation()));
    object.add("description", context.serialize(src.getDescription()));
    object.add("timestamp", context.serialize(src.getTimestamp()));
    object.add("start_timestamp", context.serialize(src.getStartTimestamp()));
    object.add("status", context.serialize(src.getStatus()));

    return object;
  }
}
