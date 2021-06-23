package io.sentry.json;

import org.jetbrains.annotations.NotNull;
import io.sentry.json.stream.JsonWriter;
import java.io.IOException;

public interface JsonSerializable {
  void toJson(@NotNull JsonWriter jsonWriter) throws IOException;
}

