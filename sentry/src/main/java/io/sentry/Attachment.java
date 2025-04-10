package io.sentry;

import io.sentry.protocol.ViewHierarchy;
import java.io.File;
import java.util.concurrent.Callable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** You can use an attachment to store additional files alongside an event or transaction. */
public final class Attachment {

  private @Nullable byte[] bytes;
  private final @Nullable JsonSerializable serializable;
  private final @Nullable Callable<byte[]> byteProvider;
  private @Nullable String pathname;
  private final @NotNull String filename;
  private final @Nullable String contentType;
  private final boolean addToTransactions;

  /** The special type of this attachment */
  private @Nullable String attachmentType = DEFAULT_ATTACHMENT_TYPE;

  /** A standard attachment without special meaning */
  private static final String DEFAULT_ATTACHMENT_TYPE = "event.attachment";

  private static final String VIEW_HIERARCHY_ATTACHMENT_TYPE = "event.view_hierarchy";

  /**
   * Initializes an Attachment with bytes and a filename. Sets addToTransaction to <code>false
   * </code>.
   *
   * @param bytes The bytes of file.
   * @param filename The name of the attachment to display in Sentry.
   */
  public Attachment(final @NotNull byte[] bytes, final @NotNull String filename) {
    this(bytes, filename, null);
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
      final @Nullable String contentType) {
    this(bytes, filename, contentType, false);
  }

  /**
   * Initializes an Attachment with bytes, a filename, a content type, and addToTransactions.
   *
   * @param bytes The bytes of file.
   * @param filename The name of the attachment to display in Sentry.
   * @param contentType The content type of the attachment.
   * @param addToTransactions <code>true</code> if the SDK should add this attachment to every
   *     {@link ITransaction} or set to <code>false</code> if it shouldn't.
   */
  public Attachment(
      final @NotNull byte[] bytes,
      final @NotNull String filename,
      final @Nullable String contentType,
      final boolean addToTransactions) {
    this(bytes, filename, contentType, DEFAULT_ATTACHMENT_TYPE, addToTransactions);
  }

  /**
   * Initializes an Attachment with bytes, a filename, a content type, and addToTransactions.
   *
   * @param bytes The bytes of file.
   * @param filename The name of the attachment to display in Sentry.
   * @param contentType The content type of the attachment.
   * @param attachmentType the attachment type.
   * @param addToTransactions <code>true</code> if the SDK should add this attachment to every
   *     {@link ITransaction} or set to <code>false</code> if it shouldn't.
   */
  public Attachment(
      final @NotNull byte[] bytes,
      final @NotNull String filename,
      final @Nullable String contentType,
      final @Nullable String attachmentType,
      final boolean addToTransactions) {
    this.bytes = bytes;
    this.serializable = null;
    this.byteProvider = null;
    this.filename = filename;
    this.contentType = contentType;
    this.attachmentType = attachmentType;
    this.addToTransactions = addToTransactions;
  }

  /**
   * Initializes an Attachment with bytes factory, a filename, a content type, and
   * addToTransactions.
   *
   * @param serializable A json serializable holding the attachment payload
   * @param filename The name of the attachment to display in Sentry.
   * @param contentType The content type of the attachment.
   * @param attachmentType the attachment type.
   * @param addToTransactions <code>true</code> if the SDK should add this attachment to every
   *     {@link ITransaction} or set to <code>false</code> if it shouldn't.
   */
  public Attachment(
      final @NotNull JsonSerializable serializable,
      final @NotNull String filename,
      final @Nullable String contentType,
      final @Nullable String attachmentType,
      final boolean addToTransactions) {
    this.bytes = null;
    this.serializable = serializable;
    this.byteProvider = null;
    this.filename = filename;
    this.contentType = contentType;
    this.attachmentType = attachmentType;
    this.addToTransactions = addToTransactions;
  }

  /**
   * Initializes an Attachment with bytes factory, a filename, a content type, and
   * addToTransactions.
   *
   * @param byteProvider A provider holding the attachment payload
   * @param filename The name of the attachment to display in Sentry.
   * @param contentType The content type of the attachment.
   * @param attachmentType the attachment type.
   * @param addToTransactions <code>true</code> if the SDK should add this attachment to every
   *     {@link ITransaction} or set to <code>false</code> if it shouldn't.
   */
  public Attachment(
      final @NotNull Callable<byte[]> byteProvider,
      final @NotNull String filename,
      final @Nullable String contentType,
      final @Nullable String attachmentType,
      final boolean addToTransactions) {
    this.bytes = null;
    this.serializable = null;
    this.byteProvider = byteProvider;
    this.filename = filename;
    this.contentType = contentType;
    this.attachmentType = attachmentType;
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
    this(pathname, filename, null);
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
      final @Nullable String contentType) {
    this(pathname, filename, contentType, DEFAULT_ATTACHMENT_TYPE, false);
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
   * @param attachmentType The attachment type.
   * @param addToTransactions <code>true</code> if the SDK should add this attachment to every
   *     {@link ITransaction} or set to <code>false</code> if it shouldn't.
   */
  public Attachment(
      final @NotNull String pathname,
      final @NotNull String filename,
      final @Nullable String contentType,
      final @Nullable String attachmentType,
      final boolean addToTransactions) {
    this.pathname = pathname;
    this.filename = filename;
    this.serializable = null;
    this.byteProvider = null;
    this.contentType = contentType;
    this.attachmentType = attachmentType;
    this.addToTransactions = addToTransactions;
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
   *     {@link ITransaction} or set to <code>false</code> if it shouldn't.
   */
  public Attachment(
      final @NotNull String pathname,
      final @NotNull String filename,
      final @Nullable String contentType,
      final boolean addToTransactions) {
    this.pathname = pathname;
    this.filename = filename;
    this.serializable = null;
    this.byteProvider = null;
    this.contentType = contentType;
    this.addToTransactions = addToTransactions;
  }

  /**
   * Initializes an Attachment with a path, a filename, a content type, addToTransactions, and
   * attachmentType.
   *
   * <p>The file located at the pathname is read lazily when the SDK captures an event or
   * transaction not when the attachment is initialized. The pathname string is converted into an
   * abstract pathname before reading the file.
   *
   * @param pathname The pathname string of the file to upload as an attachment.
   * @param filename The name of the attachment to display in Sentry.
   * @param contentType The content type of the attachment.
   * @param addToTransactions <code>true</code> if the SDK should add this attachment to every
   *     {@link ITransaction} or set to <code>false</code> if it shouldn't.
   * @param attachmentType The content type of the attachment.
   */
  public Attachment(
      final @NotNull String pathname,
      final @NotNull String filename,
      final @Nullable String contentType,
      final boolean addToTransactions,
      final @Nullable String attachmentType) {
    this.pathname = pathname;
    this.filename = filename;
    this.serializable = null;
    this.byteProvider = null;
    this.contentType = contentType;
    this.addToTransactions = addToTransactions;
    this.attachmentType = attachmentType;
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
   * Provides the bytes of the attachment.
   *
   * @return the bytes factory responsible for providing the bytes.
   */
  public @Nullable JsonSerializable getSerializable() {
    return serializable;
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
   * Gets the content type of the attachment. The server infers "application/octet-stream" if not
   * set.
   *
   * @return the content type or null if not set.
   */
  public @Nullable String getContentType() {
    return contentType;
  }

  /**
   * Returns <code>true</code> if the SDK should add this attachment to every {@link ITransaction}
   * and <code>false</code> if it shouldn't. Default is <code>false</code>.
   *
   * @return <code>true</code> if attachment should be added to every {@link ITransaction}.
   */
  boolean isAddToTransactions() {
    return addToTransactions;
  }

  /**
   * Returns the attachmentType type
   *
   * @return the attachmentType
   */
  public @Nullable String getAttachmentType() {
    return attachmentType;
  }

  public @Nullable Callable<byte[]> getByteProvider() {
    return byteProvider;
  }

  /**
   * Creates a new Screenshot Attachment
   *
   * @param screenshotBytes the array bytes of the PNG screenshot
   * @return the Attachment
   */
  public static @NotNull Attachment fromScreenshot(final byte[] screenshotBytes) {
    return new Attachment(screenshotBytes, "screenshot.png", "image/png", false);
  }

  /**
   * Creates a new Screenshot Attachment
   *
   * @param provider the mechanism providing the screenshot payload
   * @return the Attachment
   */
  public static @NotNull Attachment fromByteProvider(
      final @NotNull Callable<byte[]> provider,
      final @NotNull String filename,
      final @Nullable String contentType,
      final boolean addToTransactions) {
    return new Attachment(
        provider, filename, contentType, DEFAULT_ATTACHMENT_TYPE, addToTransactions);
  }

  /**
   * Creates a new View Hierarchy Attachment
   *
   * @param viewHierarchy the View Hierarchy
   * @return the Attachment
   */
  public static @NotNull Attachment fromViewHierarchy(final ViewHierarchy viewHierarchy) {
    return new Attachment(
        viewHierarchy,
        "view-hierarchy.json",
        "application/json",
        VIEW_HIERARCHY_ATTACHMENT_TYPE,
        false);
  }

  /**
   * Creates a new Thread Dump Attachment
   *
   * @param bytes the array bytes
   * @return the Attachment
   */
  public static @NotNull Attachment fromThreadDump(final byte[] bytes) {
    return new Attachment(bytes, "thread-dump.txt", "text/plain", false);
  }
}
