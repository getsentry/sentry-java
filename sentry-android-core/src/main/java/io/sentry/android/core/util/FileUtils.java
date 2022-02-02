package io.sentry.android.core.util;

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
    return true;
  }

  /**
   * If the relative path denotes an absolute path, it is returned back. Otherwise it is returned
   * the file relative to f For instance, `File.resolve(new File("/foo/bar"), "gav")` is `new
   * File("/foo/bar/gav")` While `File.resolve(new File("/foo/bar"), "/gav")` is `new File("/gav")`.
   *
   * @return concatenated this and [relative] paths, or just [relative] if it's absolute.
   */
  public static @Nullable File resolve(@Nullable File f, @Nullable String relative) {
    if (f == null || relative == null) return null;
    File relativeFile = new File(relative);
    // If relative path is absolute we return the relative file directly
    if (relative.length() > 0 && relative.charAt(0) == File.separatorChar) return relativeFile;
    // Otherwise we return the file relative to the parent f
    return new File(f, relative);
  }
}
