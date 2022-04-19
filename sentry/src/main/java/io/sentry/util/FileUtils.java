package io.sentry.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
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

  /**
   * Reads the content of a File into a String. If the file does not exist or is not a file, null is
   * returned. Do not use with large files, as the String is kept in memory!
   *
   * @param file file to read
   * @return a String containing all the content of the file, or null if it doesn't exists
   * @throws IOException In case of error reading the file
   */
  @SuppressWarnings("DefaultCharset")
  public static @Nullable String readText(@Nullable File file) throws IOException {
    if (file == null || !file.exists() || !file.isFile() || !file.canRead()) {
      return null;
    }
    StringBuilder contentBuilder = new StringBuilder();
    try (BufferedReader br = new BufferedReader(new FileReader(file))) {

      String line;
      // The first line doesn't need the leading \n
      if ((line = br.readLine()) != null) {
        contentBuilder.append(line);
      }
      while ((line = br.readLine()) != null) {
        contentBuilder.append("\n").append(line);
      }
    }
    return contentBuilder.toString();
  }
}
