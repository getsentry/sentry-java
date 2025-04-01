package io.sentry;

import static io.sentry.SentryLevel.DEBUG;
import static io.sentry.SentryLevel.ERROR;
import static io.sentry.SentryLevel.INFO;
import static io.sentry.SentryLevel.WARNING;
import static io.sentry.cache.EnvelopeCache.NATIVE_CRASH_MARKER_FILE;

import io.sentry.cache.EnvelopeCache;
import io.sentry.cache.IEnvelopeCache;
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

/**
 * Common cases when previous session is not ended properly (app background or crash):
 *
 * <p>- The previous session experienced Abnormal exit (ANR, OS kills app, User kills app)
 *
 * <p>- The previous session experienced native crash
 */
final class PreviousSessionFinalizer implements Runnable {

  @SuppressWarnings("CharsetObjectCanBeUsed")
  private static final Charset UTF_8 = Charset.forName("UTF-8");

  private final @NotNull SentryOptions options;

  private final @NotNull IScopes scopes;

  PreviousSessionFinalizer(final @NotNull SentryOptions options, final @NotNull IScopes scopes) {
    this.options = options;
    this.scopes = scopes;
  }

  @Override
  public void run() {
    final String cacheDirPath = options.getCacheDirPath();
    if (cacheDirPath == null) {
      if (options.getLogger().isEnabled(INFO)) {
        options.getLogger().log(INFO, "Cache dir is not set, not finalizing the previous session.");
      }
      return;
    }

    if (!options.isEnableAutoSessionTracking()) {
      if (options.getLogger().isEnabled(DEBUG)) {
        options
            .getLogger()
            .log(DEBUG, "Session tracking is disabled, bailing from previous session finalizer.");
      }
      return;
    }

    final IEnvelopeCache cache = options.getEnvelopeDiskCache();
    if (cache instanceof EnvelopeCache) {
      if (!((EnvelopeCache) cache).waitPreviousSessionFlush()) {
        if (options.getLogger().isEnabled(WARNING)) {
          options
              .getLogger()
              .log(
                  WARNING,
                  "Timed out waiting to flush previous session to its own file in session finalizer.");
        }
        return;
      }
    }

    final File previousSessionFile = EnvelopeCache.getPreviousSessionFile(cacheDirPath);
    final ISerializer serializer = options.getSerializer();

    if (previousSessionFile.exists()) {
      if (options.getLogger().isEnabled(WARNING)) {
        options.getLogger().log(WARNING, "Current session is not ended, we'd need to end it.");
      }

      try (final Reader reader =
          new BufferedReader(
              new InputStreamReader(new FileInputStream(previousSessionFile), UTF_8))) {

        final Session session = serializer.deserialize(reader, Session.class);
        if (session == null) {
          if (options.getLogger().isEnabled(ERROR)) {
            options
                .getLogger()
                .log(
                    ERROR,
                    "Stream from path %s resulted in a null envelope.",
                    previousSessionFile.getAbsolutePath());
          }
        } else {
          Date timestamp = null;
          final File crashMarkerFile =
              new File(options.getCacheDirPath(), NATIVE_CRASH_MARKER_FILE);
          if (crashMarkerFile.exists()) {
            if (options.getLogger().isEnabled(INFO)) {
              options
                  .getLogger()
                  .log(INFO, "Crash marker file exists, last Session is gonna be Crashed.");
            }

            timestamp = getTimestampFromCrashMarkerFile(crashMarkerFile);

            if (!crashMarkerFile.delete()) {
              if (options.getLogger().isEnabled(ERROR)) {
                options
                    .getLogger()
                    .log(
                        ERROR,
                        "Failed to delete the crash marker file. %s.",
                        crashMarkerFile.getAbsolutePath());
              }
            }
            session.update(Session.State.Crashed, null, true);
          }
          // if the session has abnormal mechanism, we do not overwrite its end timestamp, because
          // it's already set
          if (session.getAbnormalMechanism() == null) {
            session.end(timestamp);
          }

          // if the App. has been upgraded and there's a new version of the SDK running,
          // SdkVersion will be outdated.
          final SentryEnvelope fromSession =
              SentryEnvelope.from(serializer, session, options.getSdkVersion());
          scopes.captureEnvelope(fromSession);
        }
      } catch (Throwable e) {
        if (options.getLogger().isEnabled(ERROR)) {
          options.getLogger().log(ERROR, "Error processing previous session.", e);
        }
      }

      // at this point the previous session and its session file already became a new envelope file
      // to be sent, so deleting it
      if (!previousSessionFile.delete()) {
        if (options.getLogger().isEnabled(WARNING)) {
          options.getLogger().log(WARNING, "Failed to delete the previous session file.");
        }
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
      if (options.getLogger().isEnabled(DEBUG)) {
        options.getLogger().log(DEBUG, "Crash marker file has %s timestamp.", timestamp);
      }
      return DateUtils.getDateTime(timestamp);
    } catch (IOException e) {
      if (options.getLogger().isEnabled(ERROR)) {
        options.getLogger().log(ERROR, "Error reading the crash marker file.", e);
      }
    } catch (IllegalArgumentException e) {
      if (options.getLogger().isEnabled(ERROR)) {
        options.getLogger().log(SentryLevel.ERROR, e, "Error converting the crash timestamp.");
      }
    }
    return null;
  }
}
