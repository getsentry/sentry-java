package io.sentry.json;

import org.jetbrains.annotations.NotNull;

import io.sentry.json.stream.JsonReader;

public interface JsonDeserializer<T> {
   @NotNull T deserialize(JsonReader reader) throws Exception;
}
