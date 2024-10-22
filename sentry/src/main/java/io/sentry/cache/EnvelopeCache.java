package io.sentry.cache;

import static io.sentry.SentryLevel.DEBUG;
import static io.sentry.SentryLevel.ERROR;
import static io.sentry.SentryLevel.INFO;
import static io.sentry.SentryLevel.WARNING;
import static java.lang.String.format;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.DateUtils;
import io.sentry.Hint;
import io.sentry.SentryCrashLastRunState;
import io.sentry.SentryEnvelope;
import io.sentry.SentryEnvelopeItem;
import io.sentry.SentryItemType;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.Session;
import io.sentry.UncaughtExceptionHandlerIntegration;
import io.sentry.hints.AbnormalExit;
import io.sentry.hints.SessionEnd;
import io.sentry.hints.SessionStart;
import io.sentry.transport.NoOpEnvelopeCache;
import io.sentry.util.HintUtils;
import io.sentry.util.Objects;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Open
@ApiStatus.Internal
public class EnvelopeCache extends CacheStrategy implements IEnvelopeCache {

  /** File suffix added to all serialized envelopes files. */
  public static final String SUFFIX_ENVELOPE_FILE = ".envelope";

  public static final String PREFIX_CURRENT_SESSION_FILE = "session";

  public static final String PREFIX_PREVIOUS_SESSION_FILE = "previous_session";
  static final String SUFFIX_SESSION_FILE = ".json";
  public static final String CRASH_MARKER_FILE = "last_crash";
  public static final String NATIVE_CRASH_MARKER_FILE = ".sentry-native/" + CRASH_MARKER_FILE;

  public static final String STARTUP_CRASH_MARKER_FILE = "startup_crash";

  private final CountDownLatch previousSessionLatch;

  private final @NotNull Map<SentryEnvelope, String> fileNameMap = new WeakHashMap<>();

  public static @NotNull IEnvelopeCache create(final @NotNull SentryOptions options) {
    final String cacheDirPath = options.getCacheDirPath();
    final int maxCacheItems = options.getMaxCacheItems();
    if (cacheDirPath == null) {
      options.getLogger().log(WARNING, "cacheDirPath is null, returning NoOpEnvelopeCache");
      return NoOpEnvelopeCache.getInstance();
    } else {
      return new EnvelopeCache(options, cacheDirPath, maxCacheItems);
    }
  }

  public EnvelopeCache(
      final @NotNull SentryOptions options,
      final @NotNull String cacheDirPath,
      final int maxCacheItems) {
    super(options, cacheDirPath, maxCacheItems);
    previousSessionLatch = new CountDownLatch(1);
  }

  @Override
  public void store(final @NotNull SentryEnvelope envelope, final @NotNull Hint hint) {
    Objects.requireNonNull(envelope, "Envelope is required.");

    rotateCacheIfNeeded(allEnvelopeFiles());

    final File currentSessionFile = getCurrentSessionFile(directory.getAbsolutePath());
    final File previousSessionFile = getPreviousSessionFile(directory.getAbsolutePath());

    if (HintUtils.hasType(hint, SessionEnd.class)) {
      if (!currentSessionFile.delete()) {
        options.getLogger().log(WARNING, "Current envelope doesn't exist.");
      }
    }

    if (HintUtils.hasType(hint, AbnormalExit.class)) {
      tryEndPreviousSession(hint);
    }

    if (HintUtils.hasType(hint, SessionStart.class)) {
      if (currentSessionFile.exists()) {
        options.getLogger().log(WARNING, "Current session is not ended, we'd need to end it.");

        try (final Reader reader =
            new BufferedReader(
                new InputStreamReader(new FileInputStream(currentSessionFile), UTF_8))) {
          final Session session = serializer.getValue().deserialize(reader, Session.class);
          if (session != null) {
            writeSessionToDisk(previousSessionFile, session);
          }
        } catch (Throwable e) {
          options.getLogger().log(SentryLevel.ERROR, "Error processing session.", e);
        }
      }
      updateCurrentSession(currentSessionFile, envelope);

      boolean crashedLastRun = false;
      final File crashMarkerFile = new File(options.getCacheDirPath(), NATIVE_CRASH_MARKER_FILE);
      if (crashMarkerFile.exists()) {
        crashedLastRun = true;
      }

      // check java marker file if the native marker isnt there
      if (!crashedLastRun) {
        final File javaCrashMarkerFile = new File(options.getCacheDirPath(), CRASH_MARKER_FILE);
        if (javaCrashMarkerFile.exists()) {
          options
              .getLogger()
              .log(INFO, "Crash marker file exists, crashedLastRun will return true.");

          crashedLastRun = true;
          if (!javaCrashMarkerFile.delete()) {
            options
                .getLogger()
                .log(
                    ERROR,
                    "Failed to delete the crash marker file. %s.",
                    javaCrashMarkerFile.getAbsolutePath());
          }
        }
      }

      SentryCrashLastRunState.getInstance().setCrashedLastRun(crashedLastRun);

      flushPreviousSession();
    }

    // TODO: probably we need to update the current session file for session updates to because of
    // hardcrash events

    final File envelopeFile = getEnvelopeFile(envelope);
    if (envelopeFile.exists()) {
      options
          .getLogger()
          .log(
              WARNING,
              "Not adding Envelope to offline storage because it already exists: %s",
              envelopeFile.getAbsolutePath());
      return;
    } else {
      options
          .getLogger()
          .log(DEBUG, "Adding Envelope to offline storage: %s", envelopeFile.getAbsolutePath());
    }

    writeEnvelopeToDisk(envelopeFile, envelope);

    // write file to the disk when its about to crash so crashedLastRun can be marked on restart
    if (HintUtils.hasType(hint, UncaughtExceptionHandlerIntegration.UncaughtExceptionHint.class)) {
      writeCrashMarkerFile();
    }
  }

  /**
   * Attempts to end previous session, relying on AbnormalExit hint, marks session as abnormal with
   * abnormal mechanism and takes its timestamp.
   *
   * <p>If there was no abnormal exit, the previous session will be captured by
   * PreviousSessionFinalizer.
   *
   * @param hint a hint coming with the envelope
   */
  @SuppressWarnings("JavaUtilDate")
  private void tryEndPreviousSession(final @NotNull Hint hint) {
    final Object sdkHint = HintUtils.getSentrySdkHint(hint);
    if (sdkHint instanceof AbnormalExit) {
      final File previousSessionFile = getPreviousSessionFile(directory.getAbsolutePath());

      if (previousSessionFile.exists()) {
        options.getLogger().log(WARNING, "Previous session is not ended, we'd need to end it.");

        try (final Reader reader =
            new BufferedReader(
                new InputStreamReader(new FileInputStream(previousSessionFile), UTF_8))) {
          final Session session = serializer.getValue().deserialize(reader, Session.class);
          if (session != null) {
            final AbnormalExit abnormalHint = (AbnormalExit) sdkHint;
            final @Nullable Long abnormalExitTimestamp = abnormalHint.timestamp();
            Date timestamp = null;

            if (abnormalExitTimestamp != null) {
              timestamp = DateUtils.getDateTime(abnormalExitTimestamp);
              // sanity check if the abnormal exit actually happened when the session was alive
              final Date sessionStart = session.getStarted();
              if (sessionStart == null || timestamp.before(sessionStart)) {
                options
                    .getLogger()
                    .log(
                        WARNING,
                        "Abnormal exit happened before previous session start, not ending the session.");
                return;
              }
            }

            final String abnormalMechanism = abnormalHint.mechanism();
            session.update(Session.State.Abnormal, null, true, abnormalMechanism);
            // we have to use the actual timestamp of the Abnormal Exit here to mark the session
            // as finished at the time it happened
            session.end(timestamp);
            writeSessionToDisk(previousSessionFile, session);
          }
        } catch (Throwable e) {
          options.getLogger().log(SentryLevel.ERROR, "Error processing previous session.", e);
        }
      } else {
        options.getLogger().log(DEBUG, "No previous session file to end.");
      }
    }
  }

  private void writeCrashMarkerFile() {
    final File crashMarkerFile = new File(options.getCacheDirPath(), CRASH_MARKER_FILE);
    try (final OutputStream outputStream = new FileOutputStream(crashMarkerFile)) {
      final String timestamp = DateUtils.getTimestamp(DateUtils.getCurrentDateTime());
      outputStream.write(timestamp.getBytes(UTF_8));
      outputStream.flush();
    } catch (Throwable e) {
      options.getLogger().log(ERROR, "Error writing the crash marker file to the disk", e);
    }
  }

  private void updateCurrentSession(
      final @NotNull File currentSessionFile, final @NotNull SentryEnvelope envelope) {
    final Iterable<SentryEnvelopeItem> items = envelope.getItems();

    // we know that an envelope with a SessionStart hint has a single item inside
    if (items.iterator().hasNext()) {
      final SentryEnvelopeItem item = items.iterator().next();

      if (SentryItemType.Session.equals(item.getHeader().getType())) {
        try (final Reader reader =
            new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(item.getData()), UTF_8))) {
          final Session session = serializer.getValue().deserialize(reader, Session.class);
          if (session == null) {
            options
                .getLogger()
                .log(
                    SentryLevel.ERROR,
                    "Item of type %s returned null by the parser.",
                    item.getHeader().getType());
          } else {
            writeSessionToDisk(currentSessionFile, session);
          }
        } catch (Throwable e) {
          options.getLogger().log(ERROR, "Item failed to process.", e);
        }
      } else {
        options
            .getLogger()
            .log(
                INFO,
                "Current envelope has a different envelope type %s",
                item.getHeader().getType());
      }
    } else {
      options
          .getLogger()
          .log(INFO, "Current envelope %s is empty", currentSessionFile.getAbsolutePath());
    }
  }

  private void writeEnvelopeToDisk(
      final @NotNull File file, final @NotNull SentryEnvelope envelope) {
    if (file.exists()) {
      options
          .getLogger()
          .log(DEBUG, "Overwriting envelope to offline storage: %s", file.getAbsolutePath());
      if (!file.delete()) {
        options.getLogger().log(SentryLevel.ERROR, "Failed to delete: %s", file.getAbsolutePath());
      }
    }

    try (final OutputStream outputStream = new FileOutputStream(file)) {
      serializer.getValue().serialize(envelope, outputStream);
    } catch (Throwable e) {
      options
          .getLogger()
          .log(ERROR, e, "Error writing Envelope %s to offline storage", file.getAbsolutePath());
    }
  }

  private void writeSessionToDisk(final @NotNull File file, final @NotNull Session session) {
    if (file.exists()) {
      options
          .getLogger()
          .log(DEBUG, "Overwriting session to offline storage: %s", session.getSessionId());
      if (!file.delete()) {
        options.getLogger().log(SentryLevel.ERROR, "Failed to delete: %s", file.getAbsolutePath());
      }
    }

    try (final OutputStream outputStream = new FileOutputStream(file);
        final Writer writer = new BufferedWriter(new OutputStreamWriter(outputStream, UTF_8))) {
      serializer.getValue().serialize(session, writer);
    } catch (Throwable e) {
      options
          .getLogger()
          .log(ERROR, e, "Error writing Session to offline storage: %s", session.getSessionId());
    }
  }

  @Override
  public void discard(final @NotNull SentryEnvelope envelope) {
    Objects.requireNonNull(envelope, "Envelope is required.");

    final File envelopeFile = getEnvelopeFile(envelope);
    if (envelopeFile.exists()) {
      options
          .getLogger()
          .log(DEBUG, "Discarding envelope from cache: %s", envelopeFile.getAbsolutePath());

      if (!envelopeFile.delete()) {
        options
            .getLogger()
            .log(ERROR, "Failed to delete envelope: %s", envelopeFile.getAbsolutePath());
      }
    } else {
      options.getLogger().log(DEBUG, "Envelope was not cached: %s", envelopeFile.getAbsolutePath());
    }
  }

  /**
   * Returns the envelope's file path. If the envelope wasn't added to the cache beforehand, a
   * random file name is assigned.
   *
   * @param envelope the SentryEnvelope object
   * @return the file
   */
  private synchronized @NotNull File getEnvelopeFile(final @NotNull SentryEnvelope envelope) {
    final @NotNull String fileName;
    if (fileNameMap.containsKey(envelope)) {
      fileName = fileNameMap.get(envelope);
    } else {
      fileName = UUID.randomUUID() + SUFFIX_ENVELOPE_FILE;
      fileNameMap.put(envelope, fileName);
    }

    return new File(directory.getAbsolutePath(), fileName);
  }

  public static @NotNull File getCurrentSessionFile(final @NotNull String cacheDirPath) {
    return new File(cacheDirPath, PREFIX_CURRENT_SESSION_FILE + SUFFIX_SESSION_FILE);
  }

  public static @NotNull File getPreviousSessionFile(final @NotNull String cacheDirPath) {
    return new File(cacheDirPath, PREFIX_PREVIOUS_SESSION_FILE + SUFFIX_SESSION_FILE);
  }

  @Override
  public @NotNull Iterator<SentryEnvelope> iterator() {
    final File[] allCachedEnvelopes = allEnvelopeFiles();

    final List<SentryEnvelope> ret = new ArrayList<>(allCachedEnvelopes.length);

    for (final File file : allCachedEnvelopes) {
      try (final InputStream is = new BufferedInputStream(new FileInputStream(file))) {

        ret.add(serializer.getValue().deserializeEnvelope(is));
      } catch (FileNotFoundException e) {
        options
            .getLogger()
            .log(
                DEBUG,
                "Envelope file '%s' disappeared while converting all cached files to envelopes.",
                file.getAbsolutePath());
      } catch (IOException e) {
        options
            .getLogger()
            .log(
                ERROR,
                format("Error while reading cached envelope from file %s", file.getAbsolutePath()),
                e);
      }
    }

    return ret.iterator();
  }

  private @NotNull File[] allEnvelopeFiles() {
    if (isDirectoryValid()) {
      // lets filter the session.json here
      final File[] files =
          directory.listFiles((__, fileName) -> fileName.endsWith(SUFFIX_ENVELOPE_FILE));
      if (files != null) {
        return files;
      }
    }
    return new File[] {};
  }

  /** Awaits until the previous session (if any) is flushed to its own file. */
  public boolean waitPreviousSessionFlush() {
    try {
      return previousSessionLatch.await(
          options.getSessionFlushTimeoutMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      options.getLogger().log(DEBUG, "Timed out waiting for previous session to flush.");
    }
    return false;
  }

  public void flushPreviousSession() {
    previousSessionLatch.countDown();
  }
}
