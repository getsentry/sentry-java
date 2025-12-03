package io.sentry.android.core;

import static io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion;

import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Build;
import androidx.annotation.RequiresApi;
import io.sentry.DateUtils;
import io.sentry.Hint;
import io.sentry.ILogger;
import io.sentry.IScopes;
import io.sentry.Integration;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.android.core.ApplicationExitInfoHistoryDispatcher.ApplicationExitInfoPolicy;
import io.sentry.android.core.cache.AndroidEnvelopeCache;
import io.sentry.android.core.internal.tombstone.TombstoneParser;
import io.sentry.hints.Backfillable;
import io.sentry.hints.BlockingFlushHint;
import io.sentry.hints.NativeCrashExit;
import io.sentry.protocol.SentryId;
import io.sentry.transport.CurrentDateProvider;
import io.sentry.transport.ICurrentDateProvider;
import io.sentry.util.HintUtils;
import io.sentry.util.Objects;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public class TombstoneIntegration implements Integration, Closeable {
  private final @NotNull Context context;
  private final @NotNull ICurrentDateProvider dateProvider;
  private @Nullable SentryAndroidOptions options;

  public TombstoneIntegration(final @NotNull Context context) {
    // using CurrentDateProvider instead of AndroidCurrentDateProvider as AppExitInfo uses
    // System.currentTimeMillis
    this(context, CurrentDateProvider.getInstance());
  }

  TombstoneIntegration(
      final @NotNull Context context, final @NotNull ICurrentDateProvider dateProvider) {
    this.context = ContextUtils.getApplicationContext(context);
    this.dateProvider = dateProvider;
  }

  @Override
  public void register(@NotNull IScopes scopes, @NotNull SentryOptions options) {
    this.options =
        Objects.requireNonNull(
            (options instanceof SentryAndroidOptions) ? (SentryAndroidOptions) options : null,
            "SentryAndroidOptions is required");

    this.options
        .getLogger()
        .log(
            SentryLevel.DEBUG,
            "TombstoneIntegration enabled: %s",
            this.options.isTombstoneEnabled());

    if (this.options.isTombstoneEnabled()) {
      if (this.options.getCacheDirPath() == null) {
        this.options
            .getLogger()
            .log(SentryLevel.INFO, "Cache dir is not set, unable to process Tombstones");
        return;
      }

      try {
        options
            .getExecutorService()
            .submit(
                new ApplicationExitInfoHistoryDispatcher(
                    context,
                    scopes,
                    this.options,
                    dateProvider,
                    new TombstonePolicy(this.options)));
      } catch (Throwable e) {
        options.getLogger().log(SentryLevel.DEBUG, "Failed to start tombstone processor.", e);
      }
      options.getLogger().log(SentryLevel.DEBUG, "TombstoneIntegration installed.");
      addIntegrationToSdkVersion("Tombstone");
    }
  }

  @Override
  public void close() throws IOException {
    if (options != null) {
      options.getLogger().log(SentryLevel.DEBUG, "TombstoneIntegration removed.");
    }
  }

  @ApiStatus.Internal
  public static class TombstonePolicy implements ApplicationExitInfoPolicy {

    private final @NotNull SentryAndroidOptions options;

    public TombstonePolicy(final @NotNull SentryAndroidOptions options) {
      this.options = options;
    }

    @Override
    public @NotNull String getLabel() {
      return "Tombstone";
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    @Override
    public int getTargetReason() {
      return ApplicationExitInfo.REASON_CRASH_NATIVE;
    }

    @Override
    public boolean shouldReportHistorical() {
      return options.isReportHistoricalTombstones();
    }

    @Override
    public @Nullable Long getLastReportedTimestamp() {
      return AndroidEnvelopeCache.lastReportedTombstone(options);
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    @Override
    public @Nullable ApplicationExitInfoHistoryDispatcher.Report buildReport(
        final @NotNull ApplicationExitInfo exitInfo, final boolean enrich) {
      final SentryEvent event;
      try {
        final InputStream tombstoneInputStream = exitInfo.getTraceInputStream();
        if (tombstoneInputStream == null) {
          logTombstoneFailure(exitInfo);
          return null;
        }

        try (final TombstoneParser parser = new TombstoneParser(tombstoneInputStream)) {
          event = parser.parse();
        }
      } catch (IOException e) {
        logTombstoneFailure(exitInfo);
        return null;
      }

      final long tombstoneTimestamp = exitInfo.getTimestamp();
      event.setTimestamp(DateUtils.getDateTime(tombstoneTimestamp));

      final TombstoneHint tombstoneHint =
          new TombstoneHint(
              options.getFlushTimeoutMillis(), options.getLogger(), tombstoneTimestamp, enrich);
      final Hint hint = HintUtils.createWithTypeCheckHint(tombstoneHint);

      return new ApplicationExitInfoHistoryDispatcher.Report(event, hint, tombstoneHint);
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    private void logTombstoneFailure(final @NotNull ApplicationExitInfo exitInfo) {
      options
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Native crash report from %s does not contain a valid tombstone.",
              DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(exitInfo.getTimestamp())));
    }
  }

  @ApiStatus.Internal
  public static final class TombstoneHint extends BlockingFlushHint
      implements Backfillable, NativeCrashExit {

    private final long tombstoneTimestamp;
    private final boolean shouldEnrich;

    public TombstoneHint(
        long flushTimeoutMillis,
        @NotNull ILogger logger,
        long tombstoneTimestamp,
        boolean shouldEnrich) {
      super(flushTimeoutMillis, logger);
      this.tombstoneTimestamp = tombstoneTimestamp;
      this.shouldEnrich = shouldEnrich;
    }

    @NotNull
    @Override
    public Long timestamp() {
      return tombstoneTimestamp;
    }

    @Override
    public boolean shouldEnrich() {
      return shouldEnrich;
    }

    @Override
    public boolean isFlushable(@Nullable SentryId eventId) {
      return true;
    }

    @Override
    public void setFlushable(@NotNull SentryId eventId) {}
  }
}
