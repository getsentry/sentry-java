package io.sentry.quartz;

import io.sentry.BuildConfig;
import io.sentry.CheckIn;
import io.sentry.CheckInStatus;
import io.sentry.IScopes;
import io.sentry.ISentryLifecycleToken;
import io.sentry.ScopesAdapter;
import io.sentry.SentryIntegrationPackageStorage;
import io.sentry.SentryLevel;
import io.sentry.protocol.SentryId;
import io.sentry.util.LifecycleHelper;
import io.sentry.util.Objects;
import io.sentry.util.TracingUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobListener;

@ApiStatus.Experimental
public final class SentryJobListener implements JobListener {

  static {
    SentryIntegrationPackageStorage.getInstance()
        .addPackage("maven:io.sentry:sentry-quartz", BuildConfig.VERSION_NAME);
  }

  public static final String SENTRY_CHECK_IN_ID_KEY = "sentry-checkin-id";
  public static final String SENTRY_SLUG_KEY = "sentry-slug";
  public static final String SENTRY_SCOPE_LIFECYCLE_TOKEN_KEY = "sentry-scope-lifecycle";

  private final @NotNull IScopes scopes;

  public SentryJobListener() {
    this(ScopesAdapter.getInstance());
  }

  public SentryJobListener(final @NotNull IScopes scopes) {
    this.scopes = Objects.requireNonNull(scopes, "scopes are required");
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
      final @Nullable String maybeSlug = getSlug(context);
      if (maybeSlug == null) {
        return;
      }
      final @NotNull ISentryLifecycleToken lifecycleToken =
          scopes.forkedScopes("SentryJobListener").makeCurrent();
      TracingUtils.startNewTrace(scopes);
      final @NotNull String slug = maybeSlug;
      final @NotNull CheckIn checkIn = new CheckIn(slug, CheckInStatus.IN_PROGRESS);
      final @NotNull SentryId checkInId = scopes.captureCheckIn(checkIn);
      context.put(SENTRY_CHECK_IN_ID_KEY, checkInId);
      context.put(SENTRY_SLUG_KEY, slug);
      context.put(SENTRY_SCOPE_LIFECYCLE_TOKEN_KEY, lifecycleToken);
    } catch (Throwable t) {
      scopes
          .getOptions()
          .getLogger()
          .log(SentryLevel.ERROR, "Unable to capture check-in in jobToBeExecuted.", t);
    }
  }

  private @Nullable String getSlug(final @NotNull JobExecutionContext context) {
    final @Nullable JobDataMap jobDataMap = context.getMergedJobDataMap();
    if (jobDataMap != null) {
      final @Nullable Object o = jobDataMap.get(SENTRY_SLUG_KEY);
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
      final @Nullable Object checkInIdObjectFromContext = context.get(SENTRY_CHECK_IN_ID_KEY);
      final @Nullable Object slugObjectFromContext = context.get(SENTRY_SLUG_KEY);
      final @NotNull SentryId checkInId =
          checkInIdObjectFromContext == null
              ? new SentryId()
              : (SentryId) checkInIdObjectFromContext;
      final @Nullable String slug =
          slugObjectFromContext == null ? null : (String) slugObjectFromContext;
      if (slug != null) {
        final boolean isFailed = jobException != null;
        final @NotNull CheckInStatus status = isFailed ? CheckInStatus.ERROR : CheckInStatus.OK;
        scopes.captureCheckIn(new CheckIn(checkInId, slug, status));
      }
    } catch (Throwable t) {
      scopes
          .getOptions()
          .getLogger()
          .log(SentryLevel.ERROR, "Unable to capture check-in in jobWasExecuted.", t);
    } finally {
      LifecycleHelper.close(context.get(SENTRY_SCOPE_LIFECYCLE_TOKEN_KEY));
    }
  }
}
