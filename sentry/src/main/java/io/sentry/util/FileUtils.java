package io.sentry.util;

import java.io.File;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class FileUtils {

  /**
   * Deletes the file or directory denoted by a path. If it is a directory, all files and directory
   * inside it are deleted recursively. Note that if this operation fails then partial deletion may
   * have taken place.
   *
   * @param file file or directory to delete
   * @return true if the file/directory is successfully deleted, false otherwise
   */
  public static boolean deleteRecursively(@Nullable File file) {
    if (file == null || !file.exists()) {
      return true;
    }
    if (file.isFile()) {
      return file.delete();
    }
    File[] children = file.listFiles();
    if (children == null) return true;
    for (File f : children) {
      if (!deleteRecursively(f)) return false;
    }
    return file.delete();
  }
}
