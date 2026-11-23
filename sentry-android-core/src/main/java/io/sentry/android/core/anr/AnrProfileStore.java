package io.sentry.android.core.anr;

import io.sentry.SentryOptions;
import io.sentry.android.core.hang.HangSampleStore;
import java.io.File;
import java.io.IOException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Writes the main-thread samples of a hang to the ANR profile file, which the fatal ANR path reads
 * at the next app start. Opens the file at the first write. Does nothing without a {@link
 * SentryOptions#getCacheDirPath()}.
 */
@ApiStatus.Internal
public final class AnrProfileStore implements HangSampleStore<AnrStackTrace> {

  private final @NotNull SentryOptions options;
  private @Nullable AnrProfileManager profileManager;

  public AnrProfileStore(final @NotNull SentryOptions options) {
    this.options = options;
  }

  @Override
  public void clear() throws IOException {
    final @Nullable AnrProfileManager manager = openProfileManager();
    if (manager != null) {
      manager.clear();
    }
  }

  @Override
  public void add(final @NotNull AnrStackTrace sample) throws IOException {
    final @Nullable AnrProfileManager manager = openProfileManager();
    if (manager != null) {
      manager.add(sample);
    }
  }

  @Override
  public void close() throws IOException {
    final @Nullable AnrProfileManager manager = profileManager;
    profileManager = null;
    if (manager != null) {
      manager.close();
    }
  }

  private @Nullable AnrProfileManager openProfileManager() {
    if (profileManager == null) {
      final @Nullable String cacheDirPath = options.getCacheDirPath();
      if (cacheDirPath != null) {
        profileManager =
            new AnrProfileManager(
                options, AnrProfileRotationHelper.getFileForRecording(new File(cacheDirPath)));
      }
    }
    return profileManager;
  }
}
