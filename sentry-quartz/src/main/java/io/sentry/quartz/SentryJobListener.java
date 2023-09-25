package io.sentry.quartz;

import io.sentry.BuildConfig;
import io.sentry.CheckIn;
import io.sentry.CheckInStatus;
import io.sentry.HubAdapter;
import io.sentry.IHub;
import io.sentry.Sentry;
import io.sentry.SentryIntegrationPackageStorage;
import io.sentry.SentryLevel;
import io.sentry.protocol.SentryId;
import io.sentry.util.Objects;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobListener;
import org.quartz.Trigger;

@ApiStatus.Experimental
public final class SentryJobListener implements JobListener {

  public static final String SENTRY_CHECK_IN_ID_KEY = "sentry-checkin-id";
  public static final String SENTRY_CHECK_IN_SLUG_KEY = "sentry-checkin-slug";

  private final @NotNull IHub hub;

  public SentryJobListener() {
    this(HubAdapter.getInstance());
  }

  public SentryJobListener(final @NotNull IHub hub) {
    this.hub = Objects.requireNonNull(hub, "hub is required");
    SentryIntegrationPackageStorage.getInstance().addIntegration("Quartz");
    SentryIntegrationPackageStorage.getInstance()
        .addPackage("maven:io.sentry:sentry-quartz", BuildConfig.VERSION_NAME);
  }

  @Override
  public String getName() {
    return "sentry-job-listener";
  }

  @Override
  public void jobToBeExecuted(final @NotNull JobExecutionContext context) {
    try {
      if (isDisabled()) {
        return;
      }
      final @Nullable String maybeSlug = getSlug(context);
      if (maybeSlug == null) {
        return;
      }
      final @NotNull String slug = maybeSlug;
      final @NotNull CheckIn checkIn = new CheckIn(slug, CheckInStatus.IN_PROGRESS);
      final @NotNull SentryId checkInId = Sentry.captureCheckIn(checkIn);
      context.put(SENTRY_CHECK_IN_ID_KEY, checkInId);
      context.put(SENTRY_CHECK_IN_SLUG_KEY, slug);
    } catch (Throwable t) {
      Sentry.getCurrentHub()
          .getOptions()
          .getLogger()
          .log(SentryLevel.ERROR, "Unable to capture check-in in jobToBeExecuted.", t);
    }
  }

  private @Nullable String getSlug(final @NotNull JobExecutionContext context) {
    final Trigger trigger = context.getTrigger();
    if (trigger == null) {
      return null;
    }

    final @Nullable JobDataMap jobDataMap = trigger.getJobDataMap();
    if (jobDataMap != null) {
      final @Nullable Object o = jobDataMap.get(SENTRY_CHECK_IN_SLUG_KEY);
      if (o != null) {
        return o.toString();
      }
    }

    return null;
  }

  @Override
  public void jobExecutionVetoed(JobExecutionContext context) {
    // do nothing
  }

  @Override
  public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
    try {
      if (isDisabled()) {
        return;
      }

      final @Nullable Object checkInIdObjectFromContext = context.get(SENTRY_CHECK_IN_ID_KEY);
      final @Nullable Object slugObjectFromContext = context.get(SENTRY_CHECK_IN_SLUG_KEY);
      final @NotNull SentryId checkInId =
          checkInIdObjectFromContext == null
              ? new SentryId()
              : (SentryId) checkInIdObjectFromContext;
      final @Nullable String slug =
          slugObjectFromContext == null ? null : (String) slugObjectFromContext;
      if (slug != null) {
        final boolean isFailed = jobException != null;
        final @NotNull CheckInStatus status = isFailed ? CheckInStatus.ERROR : CheckInStatus.OK;
        Sentry.captureCheckIn(new CheckIn(checkInId, slug, status));
      }
    } catch (Throwable t) {
      Sentry.getCurrentHub()
          .getOptions()
          .getLogger()
          .log(SentryLevel.ERROR, "Unable to capture check-in in jobWasExecuted.", t);
    }
  }

  private boolean isDisabled() {
    return !hub.getOptions().isEnableAutomaticCheckIns();
  }
}
