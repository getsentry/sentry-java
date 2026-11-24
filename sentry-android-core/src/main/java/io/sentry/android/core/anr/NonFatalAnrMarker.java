package io.sentry.android.core.anr;

import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Remembers the last non-fatal ANR of this process that can also be a fatal ANR, so that the next
 * launch can tell that a fatal ANR it finds in the exit reasons was already reported. The next hang
 * of the same process deletes it.
 *
 * <p>Format, big-endian: {@code short version (1)}, {@code int pid}, {@code long startTimestampMs},
 * {@code long endTimestampMs}, both as Unix time in milliseconds.
 */
@ApiStatus.Internal
public final class NonFatalAnrMarker {

  public static final @NotNull String FILE_NAME = "last_non_fatal_anr";

  private static final short VERSION = 1;

  // Covers a user who waits at the ANR dialog before closing the app.
  private static final long GRACE_PERIOD_MS = 30_000;

  private final int pid;
  private final long startTimestampMs;
  private final long endTimestampMs;

  private NonFatalAnrMarker(final int pid, final long startTimestampMs, final long endTimestampMs) {
    this.pid = pid;
    this.startTimestampMs = startTimestampMs;
    this.endTimestampMs = endTimestampMs;
  }

  /** Whether an exit of the process {@code pid} at {@code exitTimestampMs} belongs to this hang. */
  public boolean matches(final int pid, final long exitTimestampMs) {
    return this.pid == pid
        && exitTimestampMs >= startTimestampMs
        && exitTimestampMs <= endTimestampMs + GRACE_PERIOD_MS;
  }

  /** Overwrites the previous marker. */
  public static void write(
      final @NotNull SentryOptions options,
      final int pid,
      final long startTimestampMs,
      final long endTimestampMs) {
    final @Nullable File file = markerFile(options);
    if (file == null) {
      return;
    }
    try (final @NotNull DataOutputStream dos = new DataOutputStream(new FileOutputStream(file))) {
      dos.writeShort(VERSION);
      dos.writeInt(pid);
      dos.writeLong(startTimestampMs);
      dos.writeLong(endTimestampMs);
    } catch (IOException e) {
      options.getLogger().log(SentryLevel.ERROR, "Failed to write the non-fatal ANR marker", e);
    }
  }

  /** Reads the marker, or returns null if it is missing, corrupt or of an unknown version. */
  public static @Nullable NonFatalAnrMarker read(final @NotNull SentryOptions options) {
    final @Nullable File file = markerFile(options);
    if (file == null || !file.exists()) {
      return null;
    }
    try (final @NotNull DataInputStream dis = new DataInputStream(new FileInputStream(file))) {
      if (dis.readShort() != VERSION) {
        return null;
      }
      return new NonFatalAnrMarker(dis.readInt(), dis.readLong(), dis.readLong());
    } catch (IOException e) {
      options.getLogger().log(SentryLevel.ERROR, "Failed to read the non-fatal ANR marker", e);
      return null;
    }
  }

  public static void delete(final @NotNull SentryOptions options) {
    final @Nullable File file = markerFile(options);
    if (file != null && file.exists() && !file.delete()) {
      options.getLogger().log(SentryLevel.WARNING, "Could not delete the non-fatal ANR marker");
    }
  }

  private static @Nullable File markerFile(final @NotNull SentryOptions options) {
    final @Nullable String cacheDirPath = options.getCacheDirPath();
    if (cacheDirPath == null) {
      options
          .getLogger()
          .log(SentryLevel.DEBUG, "No cacheDirPath, the non-fatal ANR marker is not available");
      return null;
    }
    return new File(cacheDirPath, FILE_NAME);
  }
}
