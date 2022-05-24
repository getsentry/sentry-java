package io.sentry;

import org.jetbrains.annotations.NotNull;

public interface IEnvelopeSender {
  void processEnvelopeFile(@NotNull String path, @NotNull Hint hint);
}
