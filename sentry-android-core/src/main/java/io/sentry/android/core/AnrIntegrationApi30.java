package io.sentry.android.core;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import io.sentry.IHub;
import io.sentry.Integration;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.exception.ExceptionMechanismException;
import io.sentry.protocol.Mechanism;
import io.sentry.util.Objects;
import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AnrIntegrationApi30 implements Integration, Closeable {

  private final @NotNull Context context;
  private @Nullable SentryAndroidOptions options;

  public AnrIntegrationApi30(final @NotNull Context context) {
    this.context = context;
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

    if (this.options.isAnrEnabled()) {
      final ActivityManager activityManager =
        (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

      List<ApplicationExitInfo> applicationExitInfoList =
        activityManager.getHistoricalProcessExitReasons(null, 0, 0);

      if (applicationExitInfoList.size() != 0) {
        reportANRs(hub, this.options, applicationExitInfoList);
        options.getLogger().log(SentryLevel.DEBUG, "AnrIntegrationApi30 installed.");
      }
    }
  }

  @SuppressLint("NewApi") // we do the check in the AnrIntegrationFactory
  private void reportANRs(
    final @NotNull IHub hub,
    final @NotNull SentryAndroidOptions options,
    final @NotNull List<ApplicationExitInfo> applicationExitInfos) {

    for (ApplicationExitInfo applicationExitInfo : applicationExitInfos) {
      if (applicationExitInfo.getReason() == ApplicationExitInfo.REASON_ANR) {
        options.getLogger().log(SentryLevel.INFO, "Found ANR in ApplicationExitInfos");

        //final Mechanism mechanism = new Mechanism();
        //mechanism.setType("ANR");
        //final ExceptionMechanismException throwable =
        //  new ExceptionMechanismException(mechanism, error, error.getThread(), true);

        //hub.captureException(throwable);
      }
    }
  }

  @Override public void close() throws IOException {
    if (options != null) {
      options.getLogger().log(SentryLevel.DEBUG, "AnrIntegrationApi30 removed.");
    }
  }
}
