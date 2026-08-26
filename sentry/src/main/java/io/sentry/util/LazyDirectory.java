package io.sentry.util;

import java.io.File;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * A filesystem directory that is created on demand rather than up front, so the (potentially
 * blocking) {@code mkdirs()} runs on the thread that first writes into it instead of on the SDK
 * init thread.
 *
 * <p>Read paths should use {@link #getFile()}, which never touches the filesystem. Write paths
 * should call {@link #getOrCreate()} once before writing. Creation is not cached: on Android the
 * cache dir lives under {@code Context.getCacheDir()}, which the system may wipe at any time, so
 * each write re-checks.
 */
@ApiStatus.Internal
public final class LazyDirectory {

  private final @NotNull File file;

  public LazyDirectory(final @NotNull String path) {
    this.file = new File(path);
  }

  /** Returns the directory without touching the filesystem. */
  public @NotNull File getFile() {
    return file;
  }

  /** Returns the directory, creating it and any missing parents if it does not exist yet. */
  public @NotNull File getOrCreate() {
    // A failed mkdirs is not reported here: callers are write paths, so the failure surfaces as the
    // write error they already log and report.
    FileUtils.createDirectory(file);
    return file;
  }
}
