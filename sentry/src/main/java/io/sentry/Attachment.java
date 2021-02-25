package io.sentry;

import java.io.File;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** You can use an attachment to store additional files alongside an event or transaction. */
public final class Attachment {

  private @Nullable byte[] bytes;
  private @Nullable String pathname;
  private final @NotNull String filename;
  private final @NotNull String contentType;
  private final boolean addToTransactions;

  /**
   * We could use Files.probeContentType(path) to determine the content type of the filename. This
   * needs a path, but file.toPath or Paths.get only work on above Android API level 26, see
   * https://developer.android.com/reference/java/nio/file/Paths. There are also ways via
   * URLConnection, but we don't want to use this in constructors. Therefore we use the default
   * content type of Sentry.
   */
  private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

  /**
   * Initializes an Attachment with bytes and a filename. Sets addToTransaction to <code>false
   * </code>.
   *
   * @param bytes The bytes of file.
   * @param filename The name of the attachment to display in Sentry.
   */
  public Attachment(final @NotNull byte[] bytes, final @NotNull String filename) {
    this(bytes, filename, DEFAULT_CONTENT_TYPE);
  }

  /**
   * Initializes an Attachment with bytes, a filename, and a content type. Sets addToTransaction to
   * <code>false</code>.
   *
   * @param bytes The bytes of file.
   * @param filename The name of the attachment to display in Sentry.
   * @param contentType The content type of the attachment.
   */
  public Attachment(
      final @NotNull byte[] bytes,
      final @NotNull String filename,
      final @NotNull String contentType) {
    this(bytes, filename, contentType, false);
  }

  /**
   * Initializes an Attachment with bytes, a filename, a content type, and addToTransactions.
   *
   * @param bytes The bytes of file.
   * @param filename The name of the attachment to display in Sentry.
   * @param contentType The content type of the attachment.
   * @param addToTransactions <code>true</code> if the SDK should add this attachment to every
   *     transaction or set to <code>false</code> if it shouldn't.
   */
  public Attachment(
      final @NotNull byte[] bytes,
      final @NotNull String filename,
      final @NotNull String contentType,
      final boolean addToTransactions) {
    this.bytes = bytes;
    this.filename = filename;
    this.contentType = contentType;
    this.addToTransactions = addToTransactions;
  }

  /**
   * Initializes an Attachment with a path. The filename of the file located at the path is used.
   * Sets addToTransaction to <code>false</code>.
   *
   * <p>The file located at the pathname is read lazily when the SDK captures an event or
   * transaction not when the attachment is initialized. The pathname string is converted into an
   * abstract pathname before reading the file.
   *
   * @param pathname The pathname string of the file to upload as an attachment.
   */
  public Attachment(final @NotNull String pathname) {
    this(pathname, new File(pathname).getName());
  }

  /**
   * Initializes an Attachment with a path and a filename. Sets addToTransaction to <code>false
   * </code>.
   *
   * <p>The file located at the pathname is read lazily when the SDK captures an event or
   * transaction not when the attachment is initialized. The pathname string is converted into an
   * abstract pathname before reading the file.
   *
   * @param pathname The pathname string of the file to upload as an attachment.
   * @param filename The name of the attachment to display in Sentry.
   */
  public Attachment(final @NotNull String pathname, final @NotNull String filename) {
    this(pathname, filename, DEFAULT_CONTENT_TYPE);
  }

  /**
   * Initializes an Attachment with a path, a filename, and a content type. Sets addToTransaction to
   * <code>false</code>.
   *
   * <p>The file located at the pathname is read lazily when the SDK captures an event or
   * transaction not when the attachment is initialized. The pathname string is converted into an
   * abstract pathname before reading the file.
   *
   * @param pathname The pathname string of the file to upload as an attachment.
   * @param filename The name of the attachment to display in Sentry.
   * @param contentType The content type of the attachment.
   */
  public Attachment(
      final @NotNull String pathname,
      final @NotNull String filename,
      final @NotNull String contentType) {
    this(pathname, filename, contentType, false);
  }

  /**
   * Initializes an Attachment with a path, a filename, a content type, and addToTransactions.
   *
   * <p>The file located at the pathname is read lazily when the SDK captures an event or
   * transaction not when the attachment is initialized. The pathname string is converted into an
   * abstract pathname before reading the file.
   *
   * @param pathname The pathname string of the file to upload as an attachment.
   * @param filename The name of the attachment to display in Sentry.
   * @param contentType The content type of the attachment.
   * @param addToTransactions <code>true</code> if the SDK should add this attachment to every
   *     transaction or set to <code>false</code> if it shouldn't.
   */
  public Attachment(
      final @NotNull String pathname,
      final @NotNull String filename,
      final @NotNull String contentType,
      final boolean addToTransactions) {
    this.pathname = pathname;
    this.filename = filename;
    this.contentType = contentType;
    this.addToTransactions = addToTransactions;
  }

  /**
   * Gets the bytes of the attachment.
   *
   * @return the bytes.
   */
  public @Nullable byte[] getBytes() {
    return bytes;
  }

  /**
   * Gets the pathname string of the attachment.
   *
   * @return the pathname string.
   */
  public @Nullable String getPathname() {
    return pathname;
  }

  /**
   * Gets the name of the attachment to display in Sentry.
   *
   * @return the filename.
   */
  public @NotNull String getFilename() {
    return filename;
  }

  /**
   * Gets the content type of the attachment. Default is "application/octet-stream".
   *
   * @return the content type.
   */
  public @NotNull String getContentType() {
    return contentType;
  }

  /**
   * Returns <code>true</code> if the SDK should add this attachment to every transaction. and
   * <code>false</code> if it shouldn't. Default is <code>false</code>.
   *
   * @return <code>true</code> if attachment should be added to every transaction.
   */
  boolean isAddToTransactions() {
    return addToTransactions;
  }
}
