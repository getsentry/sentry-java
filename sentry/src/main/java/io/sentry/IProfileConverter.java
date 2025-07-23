package io.sentry;

import io.sentry.protocol.profiling.SentryProfile;
import java.io.IOException;
import java.nio.file.Path;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Interface for converting JFR (Java Flight Recorder) files to Sentry profiles. This abstraction
 * allows different profiling implementations to be used without direct dependencies between
 * modules.
 */
@ApiStatus.Internal
public interface IProfileConverter {

  /**
   * Converts a JFR file to a SentryProfile.
   *
   * @param jfrFilePath The path to the JFR file to convert
   * @return The converted SentryProfile
   * @throws IOException If an error occurs while reading or converting the file
   */
  @NotNull
  SentryProfile convertFromFile(@NotNull Path jfrFilePath) throws IOException;
}
