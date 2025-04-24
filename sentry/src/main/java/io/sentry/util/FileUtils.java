package io.sentry.util;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
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

  private static final ThreadLocal<char[]> sharedBuffer = new ThreadLocal<>();

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

    try (Reader fr = new FileReader(file)) {
      StringBuilder sb = new StringBuilder();
      char[] buffer = sharedBuffer.get();
      if (buffer == null) {
        buffer = new char[8192];
        sharedBuffer.set(buffer);
      }

      int len;
      while ((len = fr.read(buffer)) != -1) {
        sb.append(buffer, 0, len);
      }
      return sb.toString();
    }
  }

  /**
   * Reads the content of a path into a byte array. If the path is does not exists, it's not a file,
   * can't be read or is larger than max size allowed IOException is thrown. Do not use with large
   * files, as the byte array is kept in memory!
   *
   * @param pathname file to read
   * @return a byte array containing all the content of the file
   * @throws IOException In case of error reading the file
   */
  public static byte[] readBytesFromFile(String pathname, long maxFileLength)
      throws IOException, SecurityException {
    File file = new File(pathname);

    if (!file.exists()) {
      throw new IOException(String.format("File '%s' doesn't exists", file.getName()));
    }

    if (!file.isFile()) {
      throw new IOException(
          String.format("Reading path %s failed, because it's not a file.", pathname));
    }

    if (!file.canRead()) {
      throw new IOException(
          String.format("Reading the item %s failed, because can't read the file.", pathname));
    }

    if (file.length() > maxFileLength) {
      throw new IOException(
          String.format(
              "Reading file failed, because size located at '%s' with %d bytes is bigger "
                  + "than the maximum allowed size of %d bytes.",
              pathname, file.length(), maxFileLength));
    }

    try (FileInputStream fileInputStream = new FileInputStream(pathname);
        BufferedInputStream inputStream = new BufferedInputStream(fileInputStream);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      byte[] bytes = new byte[1024];
      int length;
      int offset = 0;
      while ((length = inputStream.read(bytes)) != -1) {
        outputStream.write(bytes, offset, length);
      }
      return outputStream.toByteArray();
    }
  }
}
