package io.sentry;

import java.io.IOException;
import org.jetbrains.annotations.NotNull;

public interface JsonElementDeserializer<T> {
  @NotNull
  T deserialize(@NotNull JsonObjectReader reader) throws IOException;
}
