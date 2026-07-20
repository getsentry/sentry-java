package io.sentry.util;

import java.io.File;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * A filesystem directory that is created on demand rather than up front, so the (potentially
 * blocking) {@code mkdirs()} runs on the thread that first writes into it instead of on the SDK
 * init thread.
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
    if (!file.isDirectory()) {
      file.mkdirs();
    }
    return file;
  }
}
