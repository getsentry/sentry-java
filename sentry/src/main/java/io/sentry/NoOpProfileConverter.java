package io.sentry;

import io.sentry.protocol.profiling.SentryProfile;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;

public final class NoOpProfileConverter implements IProfileConverter {

  private static final NoOpProfileConverter instance = new NoOpProfileConverter();

  private NoOpProfileConverter() {}

  public static NoOpProfileConverter getInstance() {
    return instance;
  }

  @Override
  public @NotNull SentryProfile convertFromFile(@NotNull String jfrFilePath) throws IOException {
    return new SentryProfile();
  }
}
