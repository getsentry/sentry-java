package io.sentry.android.core;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Build;
import androidx.annotation.RequiresApi;
import io.sentry.Hint;
import io.sentry.IScopes;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.cache.EnvelopeCache;
import io.sentry.cache.IEnvelopeCache;
import io.sentry.hints.BlockingFlushHint;
import io.sentry.protocol.SentryId;
import io.sentry.transport.ICurrentDateProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
final class ApplicationExitInfoHistoryDispatcher implements Runnable {

  // using 91 to avoid timezone change hassle, 90 days is how long Sentry keeps the events
  static final long NINETY_DAYS_THRESHOLD = TimeUnit.DAYS.toMillis(91);

  private final @NotNull Context context;
  private final @NotNull IScopes scopes;
  private final @NotNull SentryAndroidOptions options;
  private final @NotNull ApplicationExitInfoPolicy policy;
  private final long threshold;

  ApplicationExitInfoHistoryDispatcher(
      final @NotNull Context context,
      final @NotNull IScopes scopes,
      final @NotNull SentryAndroidOptions options,
      final @NotNull ICurrentDateProvider dateProvider,
      final @NotNull ApplicationExitInfoPolicy policy) {
    this.context = ContextUtils.getApplicationContext(context);
    this.scopes = scopes;
    this.options = options;
    this.policy = policy;
    this.threshold = dateProvider.getCurrentTimeMillis() - NINETY_DAYS_THRESHOLD;
  }

  @RequiresApi(api = Build.VERSION_CODES.R)
  @Override
  public void run() {
    final ActivityManager activityManager =
        (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

    if (activityManager == null) {
      options.getLogger().log(SentryLevel.ERROR, "Failed to retrieve ActivityManager.");
      return;
    }

    final List<ApplicationExitInfo> applicationExitInfoList =
        activityManager.getHistoricalProcessExitReasons(null, 0, 0);

    if (applicationExitInfoList.isEmpty()) {
      options.getLogger().log(SentryLevel.DEBUG, "No records in historical exit reasons.");
      return;
    }

    waitPreviousSessionFlush();

    final List<ApplicationExitInfo> exitInfos = new ArrayList<>(applicationExitInfoList);
    final @Nullable Long lastReportedTimestamp = policy.getLastReportedTimestamp();

    final ApplicationExitInfo latest = removeLatest(exitInfos);
    if (latest == null) {
      options
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "No %ss have been found in the historical exit reasons list.",
              policy.getLabel());
      return;
    }

    if (latest.getTimestamp() < threshold) {
      options
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "Latest %s happened too long ago, returning early.",
              policy.getLabel());
      return;
    }

    if (lastReportedTimestamp != null && latest.getTimestamp() <= lastReportedTimestamp) {
      options
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "Latest %s has already been reported, returning early.",
              policy.getLabel());
      return;
    }

    if (policy.shouldReportHistorical()) {
      reportHistorical(exitInfos, lastReportedTimestamp);
    }

    report(latest, true);
  }

  private void waitPreviousSessionFlush() {
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
  }

  @RequiresApi(api = Build.VERSION_CODES.R)
  private @Nullable ApplicationExitInfo removeLatest(
      final @NotNull List<ApplicationExitInfo> exitInfos) {
    for (Iterator<ApplicationExitInfo> it = exitInfos.iterator(); it.hasNext(); ) {
      ApplicationExitInfo applicationExitInfo = it.next();
      if (applicationExitInfo.getReason() == policy.getTargetReason()) {
        it.remove();
        return applicationExitInfo;
      }
    }
    return null;
  }

  @RequiresApi(api = Build.VERSION_CODES.R)
  private void reportHistorical(
      final @NotNull List<ApplicationExitInfo> exitInfos,
      final @Nullable Long lastReportedTimestamp) {
    Collections.reverse(exitInfos);
    for (ApplicationExitInfo applicationExitInfo : exitInfos) {
      if (applicationExitInfo.getReason() == policy.getTargetReason()) {
        if (applicationExitInfo.getTimestamp() < threshold) {
          options
              .getLogger()
              .log(
                  SentryLevel.DEBUG,
                  "%s happened too long ago %s.",
                  policy.getLabel(),
                  applicationExitInfo);
          continue;
        }

        if (lastReportedTimestamp != null
            && applicationExitInfo.getTimestamp() <= lastReportedTimestamp) {
          options
              .getLogger()
              .log(
                  SentryLevel.DEBUG,
                  "%s has already been reported %s.",
                  policy.getLabel(),
                  applicationExitInfo);
          continue;
        }

        report(applicationExitInfo, false); // do not enrich past events
      }
    }
  }

  private void report(final @NotNull ApplicationExitInfo exitInfo, final boolean enrich) {
    final @Nullable Report report = policy.buildReport(exitInfo, enrich);

    if (report == null) {
      return;
    }

    final @NotNull SentryId sentryId = scopes.captureEvent(report.getEvent(), report.getHint());
    final boolean isEventDropped = sentryId.equals(SentryId.EMPTY_ID);
    if (!isEventDropped) {
      final @Nullable BlockingFlushHint flushHint = report.getFlushHint();
      if (flushHint != null && !flushHint.waitFlush()) {
        options
            .getLogger()
            .log(
                SentryLevel.WARNING,
                "Timed out waiting to flush %s event to disk. Event: %s",
                policy.getLabel(),
                report.getEvent().getEventId());
      }
    }
  }

  interface ApplicationExitInfoPolicy {
    @NotNull
    String getLabel();

    int getTargetReason();

    boolean shouldReportHistorical();

    @Nullable
    Long getLastReportedTimestamp();

    @Nullable
    Report buildReport(@NotNull ApplicationExitInfo exitInfo, boolean enrich);
  }

  public static final class Report {
    private final @NotNull SentryEvent event;
    private final @NotNull Hint hint;
    private final @Nullable BlockingFlushHint flushHint;

    Report(
        final @NotNull SentryEvent event,
        final @NotNull Hint hint,
        final @Nullable BlockingFlushHint flushHint) {
      this.event = event;
      this.hint = hint;
      this.flushHint = flushHint;
    }

    @NotNull
    public SentryEvent getEvent() {
      return event;
    }

    @NotNull
    public Hint getHint() {
      return hint;
    }

    @Nullable
    public BlockingFlushHint getFlushHint() {
      return flushHint;
    }
  }
}
