package io.sentry;

import io.sentry.cache.EnvelopeCache;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.Date;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static io.sentry.SentryLevel.DEBUG;
import static io.sentry.SentryLevel.ERROR;
import static io.sentry.SentryLevel.INFO;
import static io.sentry.SentryLevel.WARNING;
import static io.sentry.cache.EnvelopeCache.NATIVE_CRASH_MARKER_FILE;

/**
 * Common cases when previous session is not ended properly (app background or crash):
 *  - The previous session experienced Abnormal exit (ANR, OS kills app, User kills app)
 *  - The previous session experienced native crash
 */
final class PreviousSessionFinalizer implements Runnable {

  @SuppressWarnings("CharsetObjectCanBeUsed")
  private static final Charset UTF_8 = Charset.forName("UTF-8");

  private final @NotNull SentryOptions options;

  private final @NotNull IHub hub;

  PreviousSessionFinalizer(final @NotNull SentryOptions options, final @NotNull IHub hub) {
    this.options = options;
    this.hub = hub;
  }

  @Override public void run() {
    final String cacheDirPath = options.getCacheDirPath();
    if (cacheDirPath == null) {
      options.getLogger().log(INFO, "Cache dir is not set, not finalizing the previous session.");
      return;
    }

    final File previousSessionFile = EnvelopeCache.getPreviousSessionFile(cacheDirPath);
    final ISerializer serializer = options.getSerializer();

    if (previousSessionFile.exists()) {
      options.getLogger().log(WARNING, "Current session is not ended, we'd need to end it.");

      try (final Reader reader =
             new BufferedReader(
               new InputStreamReader(new FileInputStream(previousSessionFile), UTF_8))) {

        final Session session = serializer.deserialize(reader, Session.class);
        if (session == null) {
          options
            .getLogger()
            .log(
              SentryLevel.ERROR,
              "Stream from path %s resulted in a null envelope.",
              previousSessionFile.getAbsolutePath());
        } else {
          final File crashMarkerFile =
            new File(options.getCacheDirPath(), NATIVE_CRASH_MARKER_FILE);
          if (crashMarkerFile.exists()) {
            options
              .getLogger()
              .log(INFO, "Crash marker file exists, last Session is gonna be Crashed.");

            final Date timestamp = getTimestampFromCrashMarkerFile(crashMarkerFile);

            if (!crashMarkerFile.delete()) {
              options
                .getLogger()
                .log(
                  ERROR,
                  "Failed to delete the crash marker file. %s.",
                  crashMarkerFile.getAbsolutePath());
            }
            session.update(Session.State.Crashed, null, true);
            session.end(timestamp);
            // if the App. has been upgraded and there's a new version of the SDK running,
            // SdkVersion will be outdated.
            final SentryEnvelope fromSession =
              SentryEnvelope.from(serializer, session, options.getSdkVersion());
            hub.captureEnvelope(fromSession);
          } else {
            // if there was no native crash, the session has potentially experienced Abnormal exit
            // so we end it with the current timestamp, but do not send it yet, as other envelopes
            // may come later and change its attributes (status, etc.). We just save it as previous_session.json
            session.end();
            writeSessionToDisk(getPreviousSessionFile(), session);
          }
        }
      } catch (Throwable e) {
        options.getLogger().log(SentryLevel.ERROR, "Error processing previous session.", e);
      }

      // at this point the leftover session and its current session file already became a new
      // envelope file to be sent or became a previous_session file
      // so deleting it as the new session will take place.
      if (!previousSessionFile.delete()) {
        options.getLogger().log(WARNING, "Failed to delete the previous session file.");
      }
    }
  }

  /**
   * Reads the crash marker file and returns the timestamp as Date written in there
   *
   * @param markerFile the marker file
   * @return the timestamp as Date
   */
  private @Nullable Date getTimestampFromCrashMarkerFile(final @NotNull File markerFile) {
    try (final BufferedReader reader =
           new BufferedReader(new InputStreamReader(new FileInputStream(markerFile), UTF_8))) {
      final String timestamp = reader.readLine();
      options.getLogger().log(DEBUG, "Crash marker file has %s timestamp.", timestamp);
      return DateUtils.getDateTime(timestamp);
    } catch (IOException e) {
      options.getLogger().log(ERROR, "Error reading the crash marker file.", e);
    } catch (IllegalArgumentException e) {
      options.getLogger().log(SentryLevel.ERROR, e, "Error converting the crash timestamp.");
    }
    return null;
  }
}
