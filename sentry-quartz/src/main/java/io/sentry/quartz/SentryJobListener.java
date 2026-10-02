package io.sentry.quartz;

import io.sentry.BuildConfig;
import io.sentry.CheckIn;
import io.sentry.CheckInStatus;
import io.sentry.IScopes;
import io.sentry.ISentryLifecycleToken;
import io.sentry.MonitorConfig;
import io.sentry.ScopesAdapter;
import io.sentry.SentryIntegrationPackageStorage;
import io.sentry.SentryLevel;
import io.sentry.protocol.SentryId;
import io.sentry.util.LifecycleHelper;
import io.sentry.util.MonitorConfigUtils;
import io.sentry.util.Objects;
import io.sentry.util.TracingUtils;
import java.util.Locale;
import java.util.TimeZone;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.quartz.CronTrigger;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobListener;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;

@ApiStatus.Experimental
public final class SentryJobListener implements JobListener {

  static {
    SentryIntegrationPackageStorage.getInstance()
        .addPackage("maven:io.sentry:sentry-quartz", BuildConfig.VERSION_NAME);
  }

  public static final String SENTRY_CHECK_IN_ID_KEY = "sentry-checkin-id";
  public static final String SENTRY_SLUG_KEY = "sentry-slug";
  public static final String SENTRY_SCOPE_LIFECYCLE_TOKEN_KEY = "sentry-scope-lifecycle";

  /** Job data key; set to {@code false} to not send a monitor config from the trigger. */
  public static final String SENTRY_UPSERT_MONITOR_CONFIG_KEY = "sentry-upsert-monitor-config";

  private final @NotNull IScopes scopes;

  public SentryJobListener() {
    this(ScopesAdapter.getInstance());
  }

  public SentryJobListener(final @NotNull IScopes scopes) {
    this.scopes = Objects.requireNonNull(scopes, "scopes are required");
    SentryIntegrationPackageStorage.getInstance().addIntegration("Quartz");
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
      if (shouldUpsertMonitorConfig(context)) {
        checkIn.setMonitorConfig(monitorConfigFromTrigger(context.getTrigger()));
      }
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

  private boolean shouldUpsertMonitorConfig(final @NotNull JobExecutionContext context) {
    final @Nullable JobDataMap jobDataMap = context.getMergedJobDataMap();
    if (jobDataMap == null) {
      return true;
    }
    final @Nullable Object o = jobDataMap.get(SENTRY_UPSERT_MONITOR_CONFIG_KEY);
    return o == null || !"false".equalsIgnoreCase(o.toString());
  }

  private @Nullable MonitorConfig monitorConfigFromTrigger(final @Nullable Trigger trigger) {
    try {
      if (trigger instanceof CronTrigger) {
        final @NotNull CronTrigger cronTrigger = (CronTrigger) trigger;
        final @Nullable String cron = toSixFieldCron(cronTrigger.getCronExpression());
        if (cron == null) {
          return null;
        }
        final @Nullable TimeZone timeZone = cronTrigger.getTimeZone();
        return MonitorConfigUtils.fromSchedule(
            cron, timeZone == null ? null : timeZone.getID(), null, null);
      }
      if (trigger instanceof SimpleTrigger) {
        final @NotNull SimpleTrigger simpleTrigger = (SimpleTrigger) trigger;
        if (simpleTrigger.getRepeatCount() == 0) {
          return null;
        }
        return MonitorConfigUtils.fromSchedule(null, null, simpleTrigger.getRepeatInterval(), null);
      }
      return null;
    } catch (RuntimeException e) {
      scopes
          .getOptions()
          .getLogger()
          .log(
              SentryLevel.WARNING, "Could not derive a monitor config from the Quartz trigger.", e);
      return null;
    }
  }

  /** Quartz numbers days of week 1-7 from Sunday; crontab uses 0-6. Null if a year is set. */
  static @Nullable String toSixFieldCron(final @Nullable String quartzCron) {
    if (quartzCron == null) {
      return null;
    }
    final @NotNull String[] fields = quartzCron.trim().split("\\s+", -1);
    if (fields.length == 7 && !"*".equals(fields[6])) {
      return null;
    }
    if (fields.length != 6 && fields.length != 7) {
      return null;
    }
    final @Nullable String dayOfWeek = toZeroBasedDayOfWeek(fields[5]);
    if (dayOfWeek == null) {
      return null;
    }
    final @NotNull StringBuilder cron = new StringBuilder();
    for (int i = 0; i < 5; i++) {
      cron.append(fields[i]).append(' ');
    }
    return cron.append(dayOfWeek).toString();
  }

  private static @Nullable String toZeroBasedDayOfWeek(final @NotNull String field) {
    final @NotNull StringBuilder result = new StringBuilder();
    for (final @NotNull String item : field.split(",", -1)) {
      if (result.length() > 0) {
        result.append(',');
      }
      // the number after '/' or '#' is not a day
      int end = item.length();
      final int suffix = Math.max(item.indexOf('/'), item.indexOf('#'));
      if (suffix >= 0) {
        end = suffix;
      }
      final @NotNull String days = item.substring(0, end);
      final @NotNull String[] range = days.split("-", -1);
      if (range.length > 2) {
        return null;
      }
      for (int i = 0; i < range.length; i++) {
        if (i > 0) {
          result.append('-');
        }
        final @Nullable String day = toZeroBasedDay(range[i]);
        if (day == null) {
          return null;
        }
        result.append(day);
      }
      result.append(item.substring(end));
    }
    return result.toString();
  }

  private static @Nullable String toZeroBasedDay(final @NotNull String day) {
    if ("*".equals(day) || "?".equals(day)) {
      return day;
    }
    final boolean last = day.endsWith("L") || day.endsWith("l");
    final @NotNull String value = last ? day.substring(0, day.length() - 1) : day;
    if (value.matches("[1-7]")) {
      return (Integer.parseInt(value) - 1) + (last ? "L" : "");
    }
    if (value.matches("[A-Za-z]{3}")) {
      return value.toUpperCase(Locale.ROOT) + (last ? "L" : "");
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
