package io.sentry.android.core;

import static io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Build;
import androidx.annotation.RequiresApi;
import io.sentry.DateUtils;
import io.sentry.IScopes;
import io.sentry.Integration;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.android.core.internal.tombstone.TombstoneParser;
import io.sentry.cache.EnvelopeCache;
import io.sentry.cache.IEnvelopeCache;
import io.sentry.transport.CurrentDateProvider;
import io.sentry.transport.ICurrentDateProvider;
import io.sentry.util.Objects;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
            this.options.isTombstonesEnabled());

    if (this.options.isTombstonesEnabled()) {
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
          // big
          // enough to wait for it only once and we don't have to wait again in
          // PreviousSessionFinalizer
          ((EnvelopeCache) cache).flushPreviousSession();
        }
      }

      // making a deep copy as we're modifying the list
      final List<ApplicationExitInfo> exitInfos = new ArrayList<>(applicationExitInfoList);

      // search for the latest Tombstone to report it separately as we're gonna enrich it. The
      // latest
      // Tombstone will be first in the list, as it's filled last-to-first in order of appearance
      ApplicationExitInfo latestTombstone = null;
      for (ApplicationExitInfo applicationExitInfo : exitInfos) {
        if (applicationExitInfo.getReason() == ApplicationExitInfo.REASON_CRASH_NATIVE) {
          latestTombstone = applicationExitInfo;
          // remove it, so it's not reported twice
          // TODO: if we fail after this, we effectively lost the ApplicationExitInfo (maybe only
          // remove after we reported it)
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
            .log(SentryLevel.DEBUG, "Latest Tombstones happened too long ago, returning early.");
        return;
      }

      reportAsSentryEvent(latestTombstone);
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    private void reportAsSentryEvent(ApplicationExitInfo exitInfo) {
      SentryEvent event;
      try {
        TombstoneParser parser = new TombstoneParser(exitInfo.getTraceInputStream());
        event = parser.parse();
        event.setTimestamp(DateUtils.getDateTime(exitInfo.getTimestamp()));
      } catch (IOException e) {
        throw new RuntimeException(e);
      }

      scopes.captureEvent(event);
    }
  }
}
