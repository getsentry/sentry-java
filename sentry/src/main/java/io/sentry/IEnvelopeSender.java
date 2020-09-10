package io.sentry;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface IEnvelopeSender {
  void processEnvelopeFile(@NotNull String path, @Nullable Object hint);
}
