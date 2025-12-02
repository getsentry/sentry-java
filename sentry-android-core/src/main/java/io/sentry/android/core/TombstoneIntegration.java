package io.sentry.android.core;

import static io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion;

import android.app.ActivityManager;
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
import io.sentry.android.core.cache.AndroidEnvelopeCache;
import io.sentry.android.core.internal.tombstone.TombstoneParser;
import io.sentry.cache.EnvelopeCache;
import io.sentry.cache.IEnvelopeCache;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// TODO: This is very very close to ANRv2Integration in terms of mechanism. Find an abstraction
//       split that separates the equivalent mechanism from the differing policy between the two.
@ApiStatus.Internal
public class TombstoneIntegration implements Integration, Closeable {
  static final long NINETY_DAYS_THRESHOLD = TimeUnit.DAYS.toMillis(91);

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
            .submit(new TombstoneProcessor(context, scopes, this.options, dateProvider));
      } catch (Throwable e) {
        options.getLogger().log(SentryLevel.DEBUG, "Failed to start TombstoneProcessor.", e);
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
  public static class TombstoneProcessor implements Runnable {

    @NotNull private final Context context;
    @NotNull private final IScopes scopes;
    @NotNull private final SentryAndroidOptions options;
    private final long threshold;

    public TombstoneProcessor(
        @NotNull Context context,
        @NotNull IScopes scopes,
        @NotNull SentryAndroidOptions options,
        @NotNull ICurrentDateProvider dateProvider) {
      this.context = context;
      this.scopes = scopes;
      this.options = options;

      this.threshold = dateProvider.getCurrentTimeMillis() - NINETY_DAYS_THRESHOLD;
    }

    @Override
    @RequiresApi(api = Build.VERSION_CODES.R)
    public void run() {
      final ActivityManager activityManager =
          (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

      if (activityManager == null) {
        options.getLogger().log(SentryLevel.ERROR, "Failed to retrieve ActivityManager.");
        return;
      }

      final List<ApplicationExitInfo> applicationExitInfoList;
      applicationExitInfoList = activityManager.getHistoricalProcessExitReasons(null, 0, 0);

      if (applicationExitInfoList.isEmpty()) {
        options.getLogger().log(SentryLevel.DEBUG, "No records in historical exit reasons.");
        return;
      }

      final IEnvelopeCache cache = options.getEnvelopeDiskCache();
      if (cache instanceof EnvelopeCache) {
        if (options.isEnableAutoSessionTracking()
            && !((EnvelopeCache) cache).waitPreviousSessionFlush()) {
          options
              .getLogger()
              .log(
                  SentryLevel.WARNING,
                  "Timed out waiting to flush previous session to its own file.");

          // if we timed out waiting here, we can already flush the latch, because the timeout is
          // big enough to wait for it only once and we don't have to wait again in
          // PreviousSessionFinalizer
          ((EnvelopeCache) cache).flushPreviousSession();
        }
      }

      // making a deep copy as we're modifying the list
      final List<ApplicationExitInfo> exitInfos = new ArrayList<>(applicationExitInfoList);
      final @Nullable Long lastReportedTombstoneTimestamp =
          AndroidEnvelopeCache.lastReportedTombstone(options);

      // search for the latest Tombstone to report it separately as we're gonna enrich it. The
      // latest
      // Tombstone will be first in the list, as it's filled last-to-first in order of appearance
      ApplicationExitInfo latestTombstone = null;
      for (ApplicationExitInfo applicationExitInfo : exitInfos) {
        if (applicationExitInfo.getReason() == ApplicationExitInfo.REASON_CRASH_NATIVE) {
          latestTombstone = applicationExitInfo;
          // remove it, so it's not reported twice
          exitInfos.remove(applicationExitInfo);
          break;
        }
      }

      if (latestTombstone == null) {
        options
            .getLogger()
            .log(
                SentryLevel.DEBUG,
                "No Tombstones have been found in the historical exit reasons list.");
        return;
      }

      if (latestTombstone.getTimestamp() < threshold) {
        options
            .getLogger()
            .log(SentryLevel.DEBUG, "Latest Tombstone happened too long ago, returning early.");
        return;
      }

      if (lastReportedTombstoneTimestamp != null
          && latestTombstone.getTimestamp() <= lastReportedTombstoneTimestamp) {
        options
            .getLogger()
            .log(SentryLevel.DEBUG, "Latest Tombstone has already been reported, returning early.");
        return;
      }

      if (options.isReportHistoricalTombstones()) {
        // report the remainder without enriching
        reportNonEnrichedHistoricalTombstones(exitInfos, lastReportedTombstoneTimestamp);
      }

      // report the latest Tombstone with enriching, if contexts are available, otherwise report it
      // non-enriched
      reportAsSentryEvent(latestTombstone, true);
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    private void reportNonEnrichedHistoricalTombstones(
        final @NotNull List<ApplicationExitInfo> exitInfos,
        final @Nullable Long lastReportedTombstoneTimestamp) {
      // we reverse the list, because the OS puts errors in order of appearance, last-to-first
      // and we want to write a marker file after each ANR has been processed, so in case the app
      // gets killed meanwhile, we can proceed from the last reported ANR and not process the entire
      // list again
      Collections.reverse(exitInfos);
      for (ApplicationExitInfo applicationExitInfo : exitInfos) {
        if (applicationExitInfo.getReason() == ApplicationExitInfo.REASON_CRASH_NATIVE) {
          if (applicationExitInfo.getTimestamp() < threshold) {
            options
                .getLogger()
                .log(SentryLevel.DEBUG, "Tombstone happened too long ago %s.", applicationExitInfo);
            continue;
          }

          if (lastReportedTombstoneTimestamp != null
              && applicationExitInfo.getTimestamp() <= lastReportedTombstoneTimestamp) {
            options
                .getLogger()
                .log(
                    SentryLevel.DEBUG,
                    "Tombstone has already been reported %s.",
                    applicationExitInfo);
            continue;
          }

          reportAsSentryEvent(applicationExitInfo, false); // do not enrich past events
        }
      }
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    private void reportAsSentryEvent(
        final @NotNull ApplicationExitInfo exitInfo, final boolean enrich) {
      final SentryEvent event;
      try {
        final InputStream tombstoneInputStream = exitInfo.getTraceInputStream();
        if (tombstoneInputStream == null) {
          logTombstoneFailure(exitInfo);
          return;
        }

        try (final TombstoneParser parser = new TombstoneParser(tombstoneInputStream)) {
          event = parser.parse();
        }
      } catch (IOException e) {
        logTombstoneFailure(exitInfo);
        return;
      }

      final long tombstoneTimestamp = exitInfo.getTimestamp();
      event.setTimestamp(DateUtils.getDateTime(tombstoneTimestamp));

      final TombstoneHint tombstoneHint =
          new TombstoneHint(
              options.getFlushTimeoutMillis(), options.getLogger(), tombstoneTimestamp, enrich);
      final Hint hint = HintUtils.createWithTypeCheckHint(tombstoneHint);

      final @NotNull SentryId sentryId = scopes.captureEvent(event, hint);
      final boolean isEventDropped = sentryId.equals(SentryId.EMPTY_ID);
      if (!isEventDropped) {
        // Block until the event is flushed to disk and the last_reported_tombstone marker is
        // updated
        if (!tombstoneHint.waitFlush()) {
          options
              .getLogger()
              .log(
                  SentryLevel.WARNING,
                  "Timed out waiting to flush Tombstone event to disk. Event: %s",
                  event.getEventId());
        }
      }
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
