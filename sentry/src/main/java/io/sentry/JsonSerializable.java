package io.sentry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import io.sentry.vendor.gson.stream.JsonWriter;
import java.io.IOException;

@ApiStatus.Internal
public interface JsonSerializable {
  void serialize(@NotNull JsonWriter jsonWriter) throws IOException;
}
