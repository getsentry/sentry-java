package io.sentry;

import java.io.File;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Attachment {

  private @Nullable byte[] bytes;
  private @Nullable String path;
  private final String filename;
  private String contentType;

  /**
   * We could use Files.probeContentType(path) to determine the content type of the filename. This
   * needs a path, but file.toPath or Paths.get only work on above Android API level 26, see
   * https://developer.android.com/reference/java/nio/file/Paths. There are also ways via
   * URLConnection, but we don't want to use this in constructors. Therefore we use the default
   * content type of Sentry.
   */
  private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

  public Attachment(@NotNull byte[] bytes, @NotNull String filename) {
    this.bytes = bytes;
    this.filename = filename;
    this.contentType = DEFAULT_CONTENT_TYPE;
  }

  public Attachment(@NotNull String path) {
    this(path, new File(path).getName());
  }

  public Attachment(@NotNull String path, @NotNull String filename) {
    this.path = path;
    this.filename = filename;
    this.contentType = DEFAULT_CONTENT_TYPE;
  }

  public @Nullable byte[] getBytes() {
    return bytes;
  }

  public @Nullable String getPath() {
    return path;
  }

  public String getFilename() {
    return filename;
  }

  public String getContentType() {
    return contentType;
  }

  public void setContentType(String contentType) {
    this.contentType = contentType;
  }
}
