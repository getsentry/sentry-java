package io.sentry;

import java.io.IOException;
import org.jetbrains.annotations.NotNull;

public interface JsonSerializable {
  void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException;
}
