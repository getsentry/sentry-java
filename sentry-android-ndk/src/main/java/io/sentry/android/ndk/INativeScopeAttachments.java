package io.sentry.android.ndk;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Interface for managing native scope attachments. This will be implemented by {@code NativeScope}
 * in a future sentry-native-ndk release, bridging to {@code sentry_attach_file}, {@code
 * sentry_attach_bytes}, and {@code sentry_clear_attachments} in sentry-native.
 */
@ApiStatus.Internal
public interface INativeScopeAttachments {

  /**
   * Attaches a file to be sent along with native crash events.
   *
   * <p>Maps to {@code sentry_attach_file(path)} in sentry-native. The file is read lazily at crash
   * time.
   *
   * @param path absolute filesystem path to the file
   */
  void attachFile(@NotNull String path);

  /**
   * Attaches bytes to be sent along with native crash events.
   *
   * <p>Maps to {@code sentry_attach_bytes(buf, buf_len, filename)} in sentry-native.
   *
   * @param data the raw bytes to attach
   * @param filename the display name for the attachment in Sentry
   */
  void attachBytes(byte @NotNull [] data, @NotNull String filename);

  /**
   * Removes all previously added attachments.
   *
   * <p>Maps to {@code sentry_clear_attachments()} in sentry-native.
   */
  void clearAttachments();
}
