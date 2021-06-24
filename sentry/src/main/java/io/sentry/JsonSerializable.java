package io.sentry;

import io.sentry.vendor.gson.stream.JsonWriter;
import java.io.IOException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface JsonSerializable {
  void serialize(@NotNull JsonWriter jsonWriter) throws IOException;
}
