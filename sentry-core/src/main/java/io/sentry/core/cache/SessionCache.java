package io.sentry.core.cache;

import static io.sentry.core.SentryLevel.DEBUG;
import static io.sentry.core.SentryLevel.ERROR;
import static io.sentry.core.SentryLevel.WARNING;
import static java.lang.String.format;

import io.sentry.core.ISerializer;
import io.sentry.core.SentryEnvelope;
import io.sentry.core.SentryEnvelopeItem;
import io.sentry.core.SentryLevel;
import io.sentry.core.SentryOptions;
import io.sentry.core.Session;
import io.sentry.core.hints.SessionEnd;
import io.sentry.core.hints.SessionStart;
import io.sentry.core.hints.SessionUpdate;
import io.sentry.core.util.Objects;
import java.io.BufferedInputStream;
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
import java.util.Iterator;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SessionCache implements ISessionCache {

  /** File suffix added to all serialized envelopes files. */
  static final String FILE_SUFFIX = ".envelope";

  public static final String PREFIX_CURRENT_FILE = "current";

  @SuppressWarnings("CharsetObjectCanBeUsed")
  private static final Charset UTF_8 = Charset.forName("UTF-8");

  private final File directory;
  private final int maxSize;
  private final ISerializer serializer;
  private final SentryOptions options;

  public SessionCache(final SentryOptions options) {
    Objects.requireNonNull(options.getSessionsPath(), "sessions dir. path is required.");
    this.directory = new File(options.getSessionsPath());
    this.maxSize = options.getSessionsDirSize();
    this.serializer = options.getSerializer();
    this.options = options;
  }

  @Override
  public void store(@NotNull SentryEnvelope envelope, @Nullable Object hint) {
    if (getNumberOfStoredEnvelopes() >= maxSize) {
      options
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Disk cache full (respecting maxSize). Not storing envelope {}",
              envelope);
      return;
    }

    File currentEnvelopeFile = getCurrentEnvelopeFile();

    if (hint instanceof SessionEnd) {
      if (!currentEnvelopeFile.delete()) {
        options.getLogger().log(WARNING, "Current envelope doesn't exist.");
      }
    }

    if (hint instanceof SessionStart) {
      if (currentEnvelopeFile.exists()) {
        options.getLogger().log(WARNING, "Current envelope is not ended, we'd need to end it.");

        try (final Reader reader =
            new InputStreamReader(new FileInputStream(currentEnvelopeFile), UTF_8)) {

          final Session session = serializer.deserializeSession(reader);

          if (session == null) {
            options
                .getLogger()
                .log(
                    SentryLevel.ERROR,
                    "Stream from path %s resulted in a null envelope.",
                    currentEnvelopeFile.getAbsolutePath());
          } else {
            // we're ending a left over session from other runs and writing a proper envelope
            // for it.
            session.end();
            SentryEnvelope fromSession = SentryEnvelope.fromSession(serializer, session);
            File fileFromSession = getEnvelopeFile(fromSession);
            writeEnvelopeToDisk(fileFromSession, fromSession);
          }
        } catch (IOException e) {
          options.getLogger().log(SentryLevel.ERROR, "Error processing session.", e);
        }
      } else {
        Iterable<SentryEnvelopeItem> items = envelope.getItems();

        // we know that an envelope with a SessionStart hint has a single item inside
        if (items.iterator().hasNext()) {
          SentryEnvelopeItem item = items.iterator().next();

          if ("session".equals(item.getHeader().getType())) {
            try (final Reader reader =
                new InputStreamReader(new ByteArrayInputStream(item.getData()), UTF_8)) {
              final Session session = serializer.deserializeSession(reader);
              if (session == null) {
                options
                    .getLogger()
                    .log(
                        SentryLevel.ERROR,
                        "Item %d of type %s returned null by the parser.",
                        items,
                        item.getHeader().getType());
              } else {
                writeSessionToDisk(currentEnvelopeFile, session);
              }
            } catch (Exception e) {
              options.getLogger().log(ERROR, "Item failed to process.", e);
            }
          }
        }
      }
    }

    if (hint instanceof SessionUpdate) {
      writeEnvelopeToDisk(currentEnvelopeFile, envelope);
      return;
    }

    File envelopeFile = getEnvelopeFile(envelope);
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

  private void writeEnvelopeToDisk(File file, SentryEnvelope envelope) {
    if (file.exists()) {
      options
          .getLogger()
          .log(
              DEBUG,
              "Overwriting envelope to offline storage: %s",
              envelope.getHeader().getEventId());
      file.delete();
    }

    try (OutputStream fileOutputStream = new FileOutputStream(file);
        Writer wrt = new OutputStreamWriter(fileOutputStream, UTF_8)) {
      serializer.serialize(envelope, wrt);
    } catch (Exception e) {
      options
          .getLogger()
          .log(
              ERROR,
              "Error writing Envelope to offline storage: %s",
              envelope.getHeader().getEventId());
    }
  }

  private void writeSessionToDisk(File file, Session session) {
    if (file.exists()) {
      options
          .getLogger()
          .log(DEBUG, "Overwriting session to offline storage: %s", session.getSessionId());
      file.delete();
    }

    try (OutputStream fileOutputStream = new FileOutputStream(file);
        Writer wrt = new OutputStreamWriter(fileOutputStream, UTF_8)) {
      serializer.serialize(session, wrt);
    } catch (Exception e) {
      options
          .getLogger()
          .log(ERROR, "Error writing Session to offline storage: %s", session.getSessionId());
    }
  }

  @Override
  public void discard(SentryEnvelope envelope) {
    File envelopeFile = getEnvelopeFile(envelope);
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

  private File getEnvelopeFile(SentryEnvelope envelope) {
    return new File(
        directory.getAbsolutePath(), envelope.getHeader().getEventId().toString() + FILE_SUFFIX);
  }

  private File getCurrentEnvelopeFile() {
    return new File(directory.getAbsolutePath(), "current" + FILE_SUFFIX);
  }

  @NotNull
  @Override
  public Iterator<SentryEnvelope> iterator() {
    File[] allCachedEnvelopes = allEnvelopeFiles();

    List<SentryEnvelope> ret = new ArrayList<>(allCachedEnvelopes.length);

    for (File f : allCachedEnvelopes) {
      try (final InputStream is = new BufferedInputStream(new FileInputStream(f))) {

        ret.add(serializer.deserializeEnvelope(is));
      } catch (FileNotFoundException e) {
        options
            .getLogger()
            .log(
                DEBUG,
                "Envelope file '%s' disappeared while converting all cached files to envelopes.",
                f.getAbsolutePath());
      } catch (IOException e) {
        options
            .getLogger()
            .log(
                ERROR,
                format("Error while reading cached envelope from file %s", f.getAbsolutePath()),
                e);
      }
    }

    return ret.iterator();
  }

  private File[] allEnvelopeFiles() {
    if (isDirectoryValid()) {
      // lets filter the current.envelope here
      return directory.listFiles(
          (__, fileName) ->
              fileName.endsWith(FILE_SUFFIX) && !fileName.startsWith(PREFIX_CURRENT_FILE));
    }
    return new File[] {};
  }
}
