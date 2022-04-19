package io.sentry;

import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface IEnvelopeSender {
  void processEnvelopeFile(@NotNull String path, @Nullable Map<String, Object> hint);
}
