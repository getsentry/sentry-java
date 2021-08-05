package io.sentry;

import java.io.IOException;
import org.jetbrains.annotations.NotNull;

public interface JsonSerializable {
  void serialize(@NotNull JsonObjectWriter writer, @NotNull ILogger logger) throws IOException;
}
