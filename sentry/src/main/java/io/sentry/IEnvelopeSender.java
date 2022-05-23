package io.sentry;

import io.sentry.hints.Hint;
import org.jetbrains.annotations.NotNull;

public interface IEnvelopeSender {
  void processEnvelopeFile(@NotNull String path, @NotNull Hint hint);
}
