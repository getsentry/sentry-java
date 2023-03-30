package io.sentry.android.core;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Looper;
import io.sentry.DateUtils;
import io.sentry.Hint;
import io.sentry.IHub;
import io.sentry.ILogger;
import io.sentry.Integration;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.android.core.cache.AndroidEnvelopeCache;
import io.sentry.cache.EnvelopeCache;
import io.sentry.exception.ExceptionMechanismException;
import io.sentry.hints.AbnormalExit;
import io.sentry.hints.Backfillable;
import io.sentry.hints.BlockingFlushHint;
import io.sentry.protocol.Mechanism;
import io.sentry.protocol.SentryId;
import io.sentry.transport.CurrentDateProvider;
import io.sentry.transport.ICurrentDateProvider;
import io.sentry.util.HintUtils;
import io.sentry.util.Objects;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressLint("NewApi") // we check this in AnrIntegrationFactory
public class AnrV2Integration implements Integration, Closeable {

  // using 91 to avoid timezone change hassle, 90 days is how long Sentry keeps the events
  static final long NINETY_DAYS_THRESHOLD = TimeUnit.DAYS.toMillis(91);

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
    this.context = context;
    this.dateProvider = dateProvider;
  }

  @SuppressLint("NewApi") // we do the check in the AnrIntegrationFactory
  @Override
  public void register(@NotNull IHub hub, @NotNull SentryOptions options) {
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
      options
          .getExecutorService()
          .submit(new AnrProcessor(context, hub, this.options, dateProvider));
      options.getLogger().log(SentryLevel.DEBUG, "AnrV2Integration installed.");
      addIntegrationToSdkVersion();
    }
  }

  @Override
  public void close() throws IOException {
    if (options != null) {
      options.getLogger().log(SentryLevel.DEBUG, "AnrV2Integration removed.");
    }
  }

  static class AnrProcessor implements Runnable {

    private final @NotNull Context context;
    private final @NotNull IHub hub;
    private final @NotNull SentryAndroidOptions options;
    private final long threshold;

    AnrProcessor(
        final @NotNull Context context,
        final @NotNull IHub hub,
        final @NotNull SentryAndroidOptions options,
        final @NotNull ICurrentDateProvider dateProvider) {
      this.context = context;
      this.hub = hub;
      this.options = options;
      this.threshold = dateProvider.getCurrentTimeMillis() - NINETY_DAYS_THRESHOLD;
    }

    @SuppressLint("NewApi") // we check this in AnrIntegrationFactory
    @Override
    public void run() {
      final ActivityManager activityManager =
          (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

      List<ApplicationExitInfo> applicationExitInfoList =
          activityManager.getHistoricalProcessExitReasons(null, 0, 0);
      if (applicationExitInfoList.size() == 0) {
        options.getLogger().log(SentryLevel.DEBUG, "No records in historical exit reasons.");
        return;
      }

      if (!EnvelopeCache.waitPreviousSessionFlush()) {
        options
          .getLogger()
          .log(
            SentryLevel.WARNING,
            "Timed out waiting to flush previous session to its own file.");
      }

      // making a deep copy as we're modifying the list
      final List<ApplicationExitInfo> exitInfos = new ArrayList<>(applicationExitInfoList);
      final @Nullable Long lastReportedAnrTimestamp = AndroidEnvelopeCache.lastReportedAnr(options);

      // search for the latest ANR to report it separately as we're gonna enrich it. The latest
      // ANR will be first in the list, as it's filled last-to-first in order of appearance
      ApplicationExitInfo latestAnr = null;
      for (ApplicationExitInfo applicationExitInfo : exitInfos) {
        if (applicationExitInfo.getReason() == ApplicationExitInfo.REASON_ANR) {
          latestAnr = applicationExitInfo;
          // remove it, so it's not reported twice
          exitInfos.remove(applicationExitInfo);
          break;
        }
      }

      if (latestAnr == null) {
        options
            .getLogger()
            .log(SentryLevel.DEBUG, "No ANRs have been found in the historical exit reasons list.");
        return;
      }

      if (latestAnr.getTimestamp() < threshold) {
        options
            .getLogger()
            .log(SentryLevel.DEBUG, "Latest ANR happened too long ago, returning early.");
        return;
      }

      if (lastReportedAnrTimestamp != null
          && latestAnr.getTimestamp() <= lastReportedAnrTimestamp) {
        options
            .getLogger()
            .log(SentryLevel.DEBUG, "Latest ANR has already been reported, returning early.");
        return;
      }

      // report the remainder without enriching
      reportNonEnrichedHistoricalAnrs(exitInfos, lastReportedAnrTimestamp);

      // report the latest ANR with enriching, if contexts are available, otherwise report it
      // non-enriched
      reportAsSentryEvent(latestAnr, true);
    }

    private void reportNonEnrichedHistoricalAnrs(
        final @NotNull List<ApplicationExitInfo> exitInfos, final @Nullable Long lastReportedAnr) {
      // we reverse the list, because the OS puts errors in order of appearance, last-to-first
      // and we want to write a marker file after each ANR has been processed, so in case the app
      // gets killed meanwhile, we can proceed from the last reported ANR and not process the entire
      // list again
      Collections.reverse(exitInfos);
      for (ApplicationExitInfo applicationExitInfo : exitInfos) {
        if (applicationExitInfo.getReason() == ApplicationExitInfo.REASON_ANR) {
          if (applicationExitInfo.getTimestamp() < threshold) {
            options
                .getLogger()
                .log(SentryLevel.DEBUG, "ANR happened too long ago %s.", applicationExitInfo);
            continue;
          }

          if (lastReportedAnr != null && applicationExitInfo.getTimestamp() <= lastReportedAnr) {
            options
                .getLogger()
                .log(SentryLevel.DEBUG, "ANR has already been reported %s.", applicationExitInfo);
            continue;
          }

          reportAsSentryEvent(applicationExitInfo, false); // do not enrich past events
        }
      }
    }

    private void reportAsSentryEvent(
        final @NotNull ApplicationExitInfo exitInfo, final boolean shouldEnrich) {
      final long anrTimestamp = exitInfo.getTimestamp();
      final boolean isBackground =
          exitInfo.getImportance() != ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
      final Throwable anrThrowable = buildAnrThrowable(exitInfo, isBackground);
      final AnrV2Hint anrHint =
          new AnrV2Hint(
              options.getFlushTimeoutMillis(),
              options.getLogger(),
              anrTimestamp,
              shouldEnrich,
              isBackground);

      final Hint hint = HintUtils.createWithTypeCheckHint(anrHint);

      final SentryEvent event = new SentryEvent(anrThrowable);
      event.setTimestamp(DateUtils.getDateTime(anrTimestamp));
      event.setLevel(SentryLevel.FATAL);

      final @NotNull SentryId sentryId = hub.captureEvent(event, hint);
      final boolean isEventDropped = sentryId.equals(SentryId.EMPTY_ID);
      if (!isEventDropped) {
        // Block until the event is flushed to disk and the last_reported_anr marker is updated
        if (!anrHint.waitFlush()) {
          options
              .getLogger()
              .log(
                  SentryLevel.WARNING,
                  "Timed out waiting to flush ANR event to disk. Event: %s",
                  event.getEventId());
        }
      }
    }

    private @NotNull Throwable buildAnrThrowable(
        final @NotNull ApplicationExitInfo exitInfo, final boolean isBackground) {
      String message = "ANR";
      if (isBackground) {
        message = "Background " + message;
      }

      // TODO: here we should actually parse the trace file and extract the thread dump from there
      // and then we could properly get the main thread stracktrace and construct a proper exception
      final ApplicationNotResponding error =
          new ApplicationNotResponding(message, Looper.getMainLooper().getThread());
      final Mechanism mechanism = new Mechanism();
      mechanism.setType("ANRv2");
      return new ExceptionMechanismException(mechanism, error, error.getThread(), true);
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
  }
}
