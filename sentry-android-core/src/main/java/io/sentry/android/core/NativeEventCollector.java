package io.sentry.android.core;

import static io.sentry.cache.EnvelopeCache.PREFIX_CURRENT_SESSION_FILE;
import static io.sentry.cache.EnvelopeCache.PREFIX_PREVIOUS_SESSION_FILE;
import static io.sentry.cache.EnvelopeCache.STARTUP_CRASH_MARKER_FILE;

import io.sentry.SentryEnvelope;
import io.sentry.SentryEnvelopeItem;
import io.sentry.SentryEvent;
import io.sentry.SentryItemType;
import io.sentry.SentryLevel;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Collects native crash events from the outbox directory. These events can be correlated with
 * tombstone events from ApplicationExitInfo to avoid sending duplicate crash reports.
 */
@ApiStatus.Internal
public final class NativeEventCollector {

  private static final String NATIVE_PLATFORM = "native";

  // TODO: will be replaced with the correlationId once the Native SDK supports it
  private static final long TIMESTAMP_TOLERANCE_MS = 5000;

  private final @NotNull SentryAndroidOptions options;
  private final @NotNull List<NativeEventData> nativeEvents = new ArrayList<>();
  private boolean collected = false;

  public NativeEventCollector(final @NotNull SentryAndroidOptions options) {
    this.options = options;
  }

  /** Holds a native event along with its source file for later deletion. */
  public static final class NativeEventData {
    private final @NotNull SentryEvent event;
    private final @NotNull File file;
    private final @NotNull SentryEnvelope envelope;
    private final long timestampMs;

    NativeEventData(
        final @NotNull SentryEvent event,
        final @NotNull File file,
        final @NotNull SentryEnvelope envelope,
        final long timestampMs) {
      this.event = event;
      this.file = file;
      this.envelope = envelope;
      this.timestampMs = timestampMs;
    }

    public @NotNull SentryEvent getEvent() {
      return event;
    }

    public @NotNull File getFile() {
      return file;
    }

    public @NotNull SentryEnvelope getEnvelope() {
      return envelope;
    }

    public long getTimestampMs() {
      return timestampMs;
    }
  }

  /**
   * Scans the outbox directory and collects all native crash events. This method should be called
   * once before processing tombstones. Subsequent calls are no-ops.
   */
  public void collect() {
    if (collected) {
      return;
    }
    collected = true;

    final @Nullable String outboxPath = options.getOutboxPath();
    if (outboxPath == null) {
      options
          .getLogger()
          .log(SentryLevel.DEBUG, "Outbox path is null, skipping native event collection.");
      return;
    }

    final File outboxDir = new File(outboxPath);
    if (!outboxDir.isDirectory()) {
      options.getLogger().log(SentryLevel.DEBUG, "Outbox path is not a directory: %s", outboxPath);
      return;
    }

    final File[] files = outboxDir.listFiles((d, name) -> isRelevantFileName(name));
    if (files == null || files.length == 0) {
      options.getLogger().log(SentryLevel.DEBUG, "No envelope files found in outbox.");
      return;
    }

    options
        .getLogger()
        .log(SentryLevel.DEBUG, "Scanning %d files in outbox for native events.", files.length);

    for (final File file : files) {
      if (!file.isFile()) {
        continue;
      }

      final @Nullable NativeEventData nativeEventData = extractNativeEventFromFile(file);
      if (nativeEventData != null) {
        nativeEvents.add(nativeEventData);
        options
            .getLogger()
            .log(
                SentryLevel.DEBUG,
                "Found native event in outbox: %s (timestamp: %d)",
                file.getName(),
                nativeEventData.getTimestampMs());
      }
    }

    options
        .getLogger()
        .log(SentryLevel.DEBUG, "Collected %d native events from outbox.", nativeEvents.size());
  }

  /**
   * Finds a native event that matches the given tombstone timestamp. If a match is found, it is
   * removed from the internal list so it won't be matched again.
   *
   * <p>This method will lazily collect native events from the outbox on first call.
   *
   * @param tombstoneTimestampMs the timestamp from ApplicationExitInfo
   * @return the matching native event data, or null if no match found
   */
  public @Nullable NativeEventData findAndRemoveMatchingNativeEvent(
      final long tombstoneTimestampMs) {

    // Lazily collect on first use (runs on executor thread, not main thread)
    collect();

    for (final NativeEventData nativeEvent : nativeEvents) {
      final long timeDiff = Math.abs(tombstoneTimestampMs - nativeEvent.getTimestampMs());
      if (timeDiff <= TIMESTAMP_TOLERANCE_MS) {
        options
            .getLogger()
            .log(SentryLevel.DEBUG, "Matched native event by timestamp (diff: %d ms)", timeDiff);
        nativeEvents.remove(nativeEvent);
        return nativeEvent;
      }
    }

    return null;
  }

  /**
   * Deletes a native event file from the outbox.
   *
   * @param nativeEventData the native event data containing the file reference
   * @return true if the file was deleted successfully
   */
  public boolean deleteNativeEventFile(final @NotNull NativeEventData nativeEventData) {
    final File file = nativeEventData.getFile();
    try {
      if (file.delete()) {
        options
            .getLogger()
            .log(SentryLevel.DEBUG, "Deleted native event file from outbox: %s", file.getName());
        return true;
      } else {
        options
            .getLogger()
            .log(
                SentryLevel.WARNING,
                "Failed to delete native event file: %s",
                file.getAbsolutePath());
        return false;
      }
    } catch (Throwable e) {
      options
          .getLogger()
          .log(
              SentryLevel.ERROR, e, "Error deleting native event file: %s", file.getAbsolutePath());
      return false;
    }
  }

  private @Nullable NativeEventData extractNativeEventFromFile(final @NotNull File file) {
    try (final InputStream stream = new BufferedInputStream(new FileInputStream(file))) {
      final SentryEnvelope envelope = options.getEnvelopeReader().read(stream);
      if (envelope == null) {
        return null;
      }

      for (final SentryEnvelopeItem item : envelope.getItems()) {
        if (!SentryItemType.Event.equals(item.getHeader().getType())) {
          continue;
        }

        try (final Reader eventReader =
            new BufferedReader(
                new InputStreamReader(
                    new ByteArrayInputStream(item.getData()), StandardCharsets.UTF_8))) {
          final SentryEvent event =
              options.getSerializer().deserialize(eventReader, SentryEvent.class);
          if (event != null && NATIVE_PLATFORM.equals(event.getPlatform())) {
            final long timestampMs = extractTimestampMs(event);
            return new NativeEventData(event, file, envelope, timestampMs);
          }
        }
      }
    } catch (Throwable e) {
      options
          .getLogger()
          .log(SentryLevel.DEBUG, e, "Error reading envelope file: %s", file.getAbsolutePath());
    }
    return null;
  }

  private long extractTimestampMs(final @NotNull SentryEvent event) {
    final @Nullable Date timestamp = event.getTimestamp();
    if (timestamp != null) {
      return timestamp.getTime();
    }
    return 0;
  }

  private boolean isRelevantFileName(final @Nullable String fileName) {
    return fileName != null
        && !fileName.startsWith(PREFIX_CURRENT_SESSION_FILE)
        && !fileName.startsWith(PREFIX_PREVIOUS_SESSION_FILE)
        && !fileName.startsWith(STARTUP_CRASH_MARKER_FILE);
  }
}
