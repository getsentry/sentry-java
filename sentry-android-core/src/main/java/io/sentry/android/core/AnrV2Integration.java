package io.sentry.android.core;

import static io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import io.sentry.Attachment;
import io.sentry.DateUtils;
import io.sentry.Hint;
import io.sentry.ILogger;
import io.sentry.IScopes;
import io.sentry.Integration;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.android.core.cache.AndroidEnvelopeCache;
import io.sentry.android.core.internal.threaddump.Lines;
import io.sentry.android.core.internal.threaddump.ThreadDumpParser;
import io.sentry.hints.AbnormalExit;
import io.sentry.hints.Backfillable;
import io.sentry.hints.BlockingFlushHint;
import io.sentry.protocol.DebugImage;
import io.sentry.protocol.DebugMeta;
import io.sentry.protocol.Message;
import io.sentry.protocol.SentryId;
import io.sentry.protocol.SentryThread;
import io.sentry.transport.CurrentDateProvider;
import io.sentry.transport.ICurrentDateProvider;
import io.sentry.util.HintUtils;
import io.sentry.util.Objects;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressLint("NewApi") // we check this in AnrIntegrationFactory
public class AnrV2Integration implements Integration, Closeable {

  private final @NotNull Context context;
  private final @NotNull ICurrentDateProvider dateProvider;
  private @Nullable SentryAndroidOptions options;

  public AnrV2Integration(final @NotNull Context context) {
    // using CurrentDateProvider instead of AndroidCurrentDateProvider as AppExitInfo uses
    // System.currentTimeMillis
    this(context, CurrentDateProvider.getInstance());
  }

  AnrV2Integration(
      final @NotNull Context context, final @NotNull ICurrentDateProvider dateProvider) {
    this.context = ContextUtils.getApplicationContext(context);
    this.dateProvider = dateProvider;
  }

  @SuppressLint("NewApi") // we do the check in the AnrIntegrationFactory
  @Override
  public void register(@NotNull IScopes scopes, @NotNull SentryOptions options) {
    this.options =
        Objects.requireNonNull(
            (options instanceof SentryAndroidOptions) ? (SentryAndroidOptions) options : null,
            "SentryAndroidOptions is required");

    this.options
        .getLogger()
        .log(SentryLevel.DEBUG, "AnrIntegration enabled: %s", this.options.isAnrEnabled());

    if (this.options.getCacheDirPath() == null) {
      this.options
          .getLogger()
          .log(SentryLevel.INFO, "Cache dir is not set, unable to process ANRs");
      return;
    }

    if (this.options.isAnrEnabled()) {
      try {
        options
            .getExecutorService()
            .submit(
                new ApplicationExitInfoHistoryDispatcher(
                    context,
                    scopes,
                    this.options,
                    dateProvider,
                    new AnrV2Policy(scopes, this.options)));
      } catch (Throwable e) {
        options.getLogger().log(SentryLevel.DEBUG, "Failed to start ANR processor.", e);
      }
      options.getLogger().log(SentryLevel.DEBUG, "AnrV2Integration installed.");
      addIntegrationToSdkVersion("AnrV2");
    }
  }

  @Override
  public void close() throws IOException {
    if (options != null) {
      options.getLogger().log(SentryLevel.DEBUG, "AnrV2Integration removed.");
    }
  }

  private static final class AnrV2Policy
      implements ApplicationExitInfoHistoryDispatcher.ApplicationExitInfoPolicy {

    private final @NotNull IScopes scopes;
    private final @NotNull SentryAndroidOptions options;

    AnrV2Policy(final @NotNull IScopes scopes, final @NotNull SentryAndroidOptions options) {
      this.scopes = scopes;
      this.options = options;
    }

    @Override
    public @NotNull String getLabel() {
      return "ANR";
    }

    @Override
    public int getTargetReason() {
      return ApplicationExitInfo.REASON_ANR;
    }

    @Override
    public boolean shouldReportHistorical() {
      return options.isReportHistoricalAnrs();
    }

    @Override
    public @Nullable Long getLastReportedTimestamp() {
      return AndroidEnvelopeCache.lastReportedAnr(options);
    }

    @Override
    public @Nullable ApplicationExitInfoHistoryDispatcher.Report buildReport(
        final @NotNull ApplicationExitInfo exitInfo, final boolean shouldEnrich) {
      final long anrTimestamp = exitInfo.getTimestamp();
      final boolean isBackground =
          exitInfo.getImportance() != ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;

      final ParseResult result = parseThreadDump(exitInfo, isBackground);
      if (result.type == ParseResult.Type.NO_DUMP) {
        options
            .getLogger()
            .log(
                SentryLevel.WARNING,
                "Not reporting ANR event as there was no thread dump for the ANR %s",
                exitInfo.toString());
        return null;
      }
      final AnrV2Hint anrHint =
          new AnrV2Hint(
              options.getFlushTimeoutMillis(),
              options.getLogger(),
              anrTimestamp,
              shouldEnrich,
              isBackground);

      final Hint hint = HintUtils.createWithTypeCheckHint(anrHint);

      final SentryEvent event = new SentryEvent();
      if (result.type == ParseResult.Type.ERROR) {
        final Message sentryMessage = new Message();
        sentryMessage.setFormatted(
            "Sentry Android SDK failed to parse system thread dump for "
                + "this ANR. We recommend enabling [SentryOptions.isAttachAnrThreadDump] option "
                + "to attach the thread dump as plain text and report this issue on GitHub.");
        event.setMessage(sentryMessage);
      } else if (result.type == ParseResult.Type.DUMP) {
        event.setThreads(result.threads);
        if (result.debugImages != null) {
          final DebugMeta debugMeta = new DebugMeta();
          debugMeta.setImages(result.debugImages);
          event.setDebugMeta(debugMeta);
        }
      }
      event.setLevel(SentryLevel.FATAL);
      event.setTimestamp(DateUtils.getDateTime(anrTimestamp));

      if (options.isAttachAnrThreadDump()) {
        if (result.dump != null) {
          hint.setThreadDump(Attachment.fromThreadDump(result.dump));
        }
      }

      return new ApplicationExitInfoHistoryDispatcher.Report(event, hint, anrHint);
    }

    private @NotNull ParseResult parseThreadDump(
        final @NotNull ApplicationExitInfo exitInfo, final boolean isBackground) {
      final byte[] dump;

      try (final InputStream trace = exitInfo.getTraceInputStream()) {
        if (trace == null) {
          return new ParseResult(ParseResult.Type.NO_DUMP);
        }
        dump = getDumpBytes(trace);
      } catch (Throwable e) {
        options.getLogger().log(SentryLevel.WARNING, "Failed to read ANR thread dump", e);
        return new ParseResult(ParseResult.Type.NO_DUMP);
      }

      try (final BufferedReader reader =
          new BufferedReader(new InputStreamReader(new ByteArrayInputStream(dump)))) {
        final Lines lines = Lines.readLines(reader);

        final ThreadDumpParser threadDumpParser = new ThreadDumpParser(options, isBackground);
        threadDumpParser.parse(lines);

        final @NotNull List<SentryThread> threads = threadDumpParser.getThreads();
        final @NotNull List<DebugImage> debugImages = threadDumpParser.getDebugImages();

        if (threads.isEmpty()) {
          // if the list is empty this means the system failed to capture a proper thread dump of
          // the android threads, and only contains kernel-level threads and statuses, those ANRs
          // are not actionable and neither they are reported by Google Play Console, so we just
          // fall back to not reporting them
          return new ParseResult(ParseResult.Type.NO_DUMP);
        }
        return new ParseResult(ParseResult.Type.DUMP, dump, threads, debugImages);
      } catch (Throwable e) {
        options.getLogger().log(SentryLevel.WARNING, "Failed to parse ANR thread dump", e);
        return new ParseResult(ParseResult.Type.ERROR, dump);
      }
    }

    private byte[] getDumpBytes(final @NotNull InputStream trace) throws IOException {
      try (final ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {

        int nRead;
        final byte[] data = new byte[1024];

        while ((nRead = trace.read(data, 0, data.length)) != -1) {
          buffer.write(data, 0, nRead);
        }

        return buffer.toByteArray();
      }
    }
  }

  @ApiStatus.Internal
  public static final class AnrV2Hint extends BlockingFlushHint
      implements Backfillable, AbnormalExit {

    private final long timestamp;

    private final boolean shouldEnrich;

    private final boolean isBackgroundAnr;

    public AnrV2Hint(
        final long flushTimeoutMillis,
        final @NotNull ILogger logger,
        final long timestamp,
        final boolean shouldEnrich,
        final boolean isBackgroundAnr) {
      super(flushTimeoutMillis, logger);
      this.timestamp = timestamp;
      this.shouldEnrich = shouldEnrich;
      this.isBackgroundAnr = isBackgroundAnr;
    }

    @Override
    public boolean ignoreCurrentThread() {
      return false;
    }

    @NotNull
    @Override
    public Long timestamp() {
      return timestamp;
    }

    @Override
    public boolean shouldEnrich() {
      return shouldEnrich;
    }

    @Override
    public String mechanism() {
      return isBackgroundAnr ? "anr_background" : "anr_foreground";
    }

    @Override
    public boolean isFlushable(@Nullable SentryId eventId) {
      return true;
    }

    @Override
    public void setFlushable(@NotNull SentryId eventId) {}
  }

  static final class ParseResult {

    enum Type {
      DUMP,
      NO_DUMP,
      ERROR
    }

    final Type type;
    final byte[] dump;
    final @Nullable List<SentryThread> threads;
    final @Nullable List<DebugImage> debugImages;

    ParseResult(final @NotNull Type type) {
      this.type = type;
      this.dump = null;
      this.threads = null;
      this.debugImages = null;
    }

    ParseResult(final @NotNull Type type, final byte[] dump) {
      this.type = type;
      this.dump = dump;
      this.threads = null;
      this.debugImages = null;
    }

    ParseResult(
        final @NotNull Type type,
        final byte[] dump,
        final @Nullable List<SentryThread> threads,
        final @Nullable List<DebugImage> debugImages) {
      this.type = type;
      this.dump = dump;
      this.threads = threads;
      this.debugImages = debugImages;
    }
  }
}
