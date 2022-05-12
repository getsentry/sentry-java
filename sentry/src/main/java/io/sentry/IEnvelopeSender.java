package io.sentry;

import io.sentry.hints.Hints;
import org.jetbrains.annotations.NotNull;

public interface IEnvelopeSender {
  void processEnvelopeFile(@NotNull String path, @NotNull Hints hints);
}
