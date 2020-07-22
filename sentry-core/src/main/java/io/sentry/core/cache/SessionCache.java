package io.sentry.core.cache;

import static io.sentry.core.SentryLevel.DEBUG;
import static io.sentry.core.SentryLevel.ERROR;
import static io.sentry.core.SentryLevel.INFO;
import static io.sentry.core.SentryLevel.WARNING;
import static java.lang.String.format;

import io.sentry.core.DateUtils;
import io.sentry.core.ISerializer;
import io.sentry.core.SentryEnvelope;
import io.sentry.core.SentryEnvelopeItem;
import io.sentry.core.SentryItemType;
import io.sentry.core.SentryLevel;
import io.sentry.core.SentryOptions;
import io.sentry.core.Session;
import io.sentry.core.hints.SessionEnd;
import io.sentry.core.hints.SessionStart;
import io.sentry.core.hints.SessionUpdate;
import io.sentry.core.util.Objects;
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
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SessionCache implements IEnvelopeCache {

  /** File suffix added to all serialized envelopes files. */
  static final String SUFFIX_ENVELOPE_FILE = ".envelope";

  public static final String PREFIX_CURRENT_SESSION_FILE = "session";
  static final String SUFFIX_CURRENT_SESSION_FILE = ".json";
  static final String CRASH_MARKER_FILE = ".sentry-native/last_crash";

  @SuppressWarnings("CharsetObjectCanBeUsed")
  private static final Charset UTF_8 = Charset.forName("UTF-8");

  private final @NotNull File directory;
  private final int maxSize;
  private final @NotNull ISerializer serializer;
  private final @NotNull SentryOptions options;

  private final @NotNull Map<SentryEnvelope, String> fileNameMap = new WeakHashMap<>();

  public SessionCache(final @NotNull SentryOptions options) {
    Objects.requireNonNull(options.getSessionsPath(), "sessions dir. path is required.");
    this.directory = new File(options.getSessionsPath());
    this.maxSize = options.getSessionsDirSize();
    this.serializer = options.getSerializer();
    this.options = options;
  }

  @Override
  public void store(final @NotNull SentryEnvelope envelope, final @Nullable Object hint) {
    Objects.requireNonNull(envelope, "Envelope is required.");

    if (getNumberOfStoredEnvelopes() >= maxSize) {
      options
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Disk cache full (respecting maxSize). Not storing envelope {}",
              envelope);
      return;
    }

    final File currentSessionFile = getCurrentSessionFile();

    if (hint instanceof SessionEnd) {
      if (!currentSessionFile.delete()) {
        options.getLogger().log(WARNING, "Current envelope doesn't exist.");
      }
    }

    if (hint instanceof SessionStart) {

      // TODO: should we move this to AppLifecycleIntegration? and do on SDK init? but it's too much
      // on main-thread
      if (currentSessionFile.exists()) {
        options.getLogger().log(WARNING, "Current session is not ended, we'd need to end it.");

        try (final Reader reader =
            new BufferedReader(
                new InputStreamReader(new FileInputStream(currentSessionFile), UTF_8))) {

          final Session session = serializer.deserializeSession(reader);
          if (session == null) {
            options
                .getLogger()
                .log(
                    SentryLevel.ERROR,
                    "Stream from path %s resulted in a null envelope.",
                    currentSessionFile.getAbsolutePath());
          } else {
            final File crashMarkerFile = new File(options.getCacheDirPath(), CRASH_MARKER_FILE);
            Date timestamp = null;
            if (crashMarkerFile.exists()) {
              options
                  .getLogger()
                  .log(INFO, "Crash marker file exists, last Session is gonna be Crashed.");

              timestamp = getTimestampFromCrashMarkerFile(crashMarkerFile);
              if (!crashMarkerFile.delete()) {
                options
                    .getLogger()
                    .log(
                        ERROR,
                        "Failed to delete the crash marker file. %s.",
                        crashMarkerFile.getAbsolutePath());
              }
              session.update(Session.State.Crashed, null, true);
            }

            session.end(timestamp);
            // if the App. has been upgraded and there's a new version of the SDK running,
            // SdkVersion will be outdated.
            final SentryEnvelope fromSession =
                SentryEnvelope.fromSession(serializer, session, options.getSdkVersion());
            final File fileFromSession = getEnvelopeFile(fromSession);
            writeEnvelopeToDisk(fileFromSession, fromSession);
          }
        } catch (Exception e) {
          options.getLogger().log(SentryLevel.ERROR, "Error processing session.", e);
        }

        // at this point the leftover session and its current session file already became a new
        // envelope file to be sent
        // so deleting it as the new session will take place.
        if (!currentSessionFile.delete()) {
          options.getLogger().log(WARNING, "Failed to delete the current session file.");
        }
      }
      updateCurrentSession(currentSessionFile, envelope);
    }

    if (hint instanceof SessionUpdate) {
      updateCurrentSession(currentSessionFile, envelope);
      return;
    }

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
  }

  /**
   * Reads the crash marker file and returns the timestamp as Date written in there
   *
   * @param markerFile the marker file
   * @return the timestamp as Date
   */
  private Date getTimestampFromCrashMarkerFile(final @NotNull File markerFile) {
    try (final BufferedReader reader =
        new BufferedReader(new InputStreamReader(new FileInputStream(markerFile), UTF_8))) {
      final String timestamp = reader.readLine();
      options.getLogger().log(DEBUG, "Crash marker file has %s timestamp.", timestamp);
      return DateUtils.getDateTime(timestamp);
    } catch (IOException e) {
      options.getLogger().log(ERROR, "Error reading the crash marker file.", e);
    }
    return null;
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
          final Session session = serializer.deserializeSession(reader);
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
        } catch (Exception e) {
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

    try (final OutputStream outputStream = new FileOutputStream(file);
        final Writer writer = new BufferedWriter(new OutputStreamWriter(outputStream, UTF_8))) {
      serializer.serialize(envelope, writer);
    } catch (Exception e) {
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
      serializer.serialize(session, writer);
    } catch (Exception e) {
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

  private int getNumberOfStoredEnvelopes() {
    return allEnvelopeFiles().length;
  }

  private boolean isDirectoryValid() {
    if (!directory.isDirectory() || !directory.canWrite() || !directory.canRead()) {
      options
          .getLogger()
          .log(
              ERROR,
              "The directory for caching Sentry envelopes is inaccessible.: %s",
              directory.getAbsolutePath());
      return false;
    }
    return true;
  }

  /**
   * Returns the envelope's file path. If the envelope has no eventId header, it generates a random
   * file name to it.
   *
   * @param envelope the SentryEnvelope object
   * @return the file
   */
  private synchronized @NotNull File getEnvelopeFile(final @NotNull SentryEnvelope envelope) {
    String fileName;
    if (fileNameMap.containsKey(envelope)) {
      fileName = fileNameMap.get(envelope);
    } else {
      if (envelope.getHeader().getEventId() != null) {
        fileName = envelope.getHeader().getEventId().toString();
      } else {
        fileName = UUID.randomUUID().toString();
      }
      fileName += SUFFIX_ENVELOPE_FILE;
      fileNameMap.put(envelope, fileName);
    }

    return new File(directory.getAbsolutePath(), fileName);
  }

  private @NotNull File getCurrentSessionFile() {
    return new File(
        directory.getAbsolutePath(), PREFIX_CURRENT_SESSION_FILE + SUFFIX_CURRENT_SESSION_FILE);
  }

  @Override
  public @NotNull Iterator<SentryEnvelope> iterator() {
    final File[] allCachedEnvelopes = allEnvelopeFiles();

    final List<SentryEnvelope> ret = new ArrayList<>(allCachedEnvelopes.length);

    for (final File file : allCachedEnvelopes) {
      try (final InputStream is = new BufferedInputStream(new FileInputStream(file))) {

        ret.add(serializer.deserializeEnvelope(is));
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
      return directory.listFiles((__, fileName) -> fileName.endsWith(SUFFIX_ENVELOPE_FILE));
    }
    return new File[] {};
  }
}
