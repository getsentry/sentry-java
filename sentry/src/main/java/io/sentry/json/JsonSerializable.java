package io.sentry.json;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import io.sentry.json.stream.JsonWriter;
import java.io.IOException;

@ApiStatus.Internal
public interface JsonSerializable {
  void serialize(@NotNull JsonWriter jsonWriter) throws IOException;
}
