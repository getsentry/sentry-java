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
import io.sentry.android.core.hints.AnrHint;
import io.sentry.exception.ExceptionMechanismException;
import io.sentry.hints.BlockingFlushHint;
import io.sentry.protocol.Mechanism;
import io.sentry.protocol.SentryId;
import io.sentry.transport.CurrentDateProvider;
import io.sentry.transport.ICurrentDateProvider;
import io.sentry.util.HintUtils;
import io.sentry.util.Objects;
import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AnrIntegrationV2 implements Integration, Closeable {

  // using 91 to avoid timezone change hassle, 90 days is how long Sentry keeps the events
  private static final long NINETY_DAYS_THRESHOLD = TimeUnit.DAYS.toMillis(91);

  private final @NotNull Context context;
  private final @NotNull ICurrentDateProvider dateProvider;
  private @Nullable SentryAndroidOptions options;

  public AnrIntegrationV2(final @NotNull Context context) {
    // using CurrentDateProvider instead of AndroidCurrentDateProvider as AppExitInfo uses System.currentTimeMillis
    this(context, CurrentDateProvider.getInstance());
  }

  AnrIntegrationV2(final @NotNull Context context,
    final @NotNull ICurrentDateProvider dateProvider) {
    this.context = context;
    this.dateProvider = dateProvider;
  }

  @SuppressLint("NewApi") // we do the check in the AnrIntegrationFactory
  @Override public void register(@NotNull IHub hub, @NotNull SentryOptions options) {
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
      final ActivityManager activityManager =
        (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

      List<ApplicationExitInfo> applicationExitInfoList =
        activityManager.getHistoricalProcessExitReasons(null, 0, 0);

      if (applicationExitInfoList.size() != 0) {
        options.getExecutorService()
          .submit(
            new AnrProcessor(
              applicationExitInfoList,
              hub,
              this.options,
              dateProvider)
          );
      } else {
        options.getLogger().log(SentryLevel.DEBUG, "No records in historical exit reasons.");
      }
      options.getLogger().log(SentryLevel.DEBUG, "AnrIntegrationV2 installed.");
    }
  }

  @Override public void close() throws IOException {
    if (options != null) {
      options.getLogger().log(SentryLevel.DEBUG, "AnrIntegrationV2 removed.");
    }
  }

  static class AnrProcessor implements Runnable {

    private final @NotNull List<ApplicationExitInfo> exitInfos;
    private final @NotNull IHub hub;
    private final @NotNull SentryAndroidOptions options;
    private final @NotNull ICurrentDateProvider dateProvider;

    AnrProcessor(
      final @NotNull List<ApplicationExitInfo> exitInfos,
      final @NotNull IHub hub,
      final @NotNull SentryAndroidOptions options,
      final @NotNull ICurrentDateProvider dateProvider) {
      this.exitInfos = exitInfos;
      this.hub = hub;
      this.options = options;
      this.dateProvider = dateProvider;
    }

    @SuppressLint("NewApi") // we check this in AnrIntegrationFactory
    @Override public void run() {
      final long threshold = dateProvider.getCurrentTimeMillis() - NINETY_DAYS_THRESHOLD;
      final long lastReportedAnr = AndroidEnvelopeCache.lastReportedAnr(options);

      // we reverse the list, because the OS puts errors in order of appearance, last-to-first
      // and we want to write a marker file after each ANR has been processed, so in case the app
      // gets killed meanwhile, we can proceed from the last reported ANR and processed the whole list
      Collections.reverse(exitInfos);
      for (ApplicationExitInfo applicationExitInfo : exitInfos) {
        if (applicationExitInfo.getReason() == ApplicationExitInfo.REASON_ANR) {
          final long anrTimestamp = applicationExitInfo.getTimestamp();
          if (anrTimestamp < threshold) {
            options
              .getLogger()
              .log(SentryLevel.DEBUG, "ANR happened too long ago %s.", applicationExitInfo);
            continue;
          }

          if (anrTimestamp <= lastReportedAnr) {
            options
              .getLogger()
              .log(SentryLevel.DEBUG, "ANR has already been reported %s", applicationExitInfo);
            continue;
          }

          final Throwable anrThrowable = buildAnrThrowable(applicationExitInfo);
          final AnrV2Hint anrHint =
            new AnrV2Hint(options.getFlushTimeoutMillis(), options.getLogger(), anrTimestamp);

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
      }
    }

    @SuppressLint("NewApi") // we check this in AnrIntegrationFactory
    private @NotNull Throwable buildAnrThrowable(final @NotNull ApplicationExitInfo exitInfo) {
      final boolean isBackground = exitInfo.getImportance() !=
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;

      String message = "ANR";
      if (isBackground) {
        message = "Background " + message;
      }

      final ApplicationNotResponding error =
        new ApplicationNotResponding(message, Looper.getMainLooper().getThread());
      final Mechanism mechanism = new Mechanism();
      mechanism.setType("ANR");
      return new ExceptionMechanismException(mechanism, error, error.getThread(), true);
    }
  }

  @ApiStatus.Internal
  public static final class AnrV2Hint extends BlockingFlushHint implements AnrHint {

    private final long timestamp;

    AnrV2Hint(final long flushTimeoutMillis, final @NotNull ILogger logger, final long timestamp) {
      super(flushTimeoutMillis, logger);
      this.timestamp = timestamp;
    }

    @Override public long timestamp() {
      return timestamp;
    }
  }
}
