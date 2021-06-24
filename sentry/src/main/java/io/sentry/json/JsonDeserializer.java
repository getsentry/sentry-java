package io.sentry.json;

import org.jetbrains.annotations.NotNull;

public interface JsonDeserializer<T> {
   @NotNull T fromJson(String json) throws Exception;
}
