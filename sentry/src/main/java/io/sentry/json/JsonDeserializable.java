package io.sentry.json;

import org.jetbrains.annotations.NotNull;

public interface JsonDeserializable<T> {
   @NotNull T fromJson(String json) throws Exception;
}
