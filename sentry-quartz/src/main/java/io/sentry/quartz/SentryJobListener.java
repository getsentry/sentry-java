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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Pattern;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.quartz.CronTrigger;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobListener;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
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

  /**
   * Job or trigger data key. The listener sends a monitor config from the trigger with check-ins by
   * default; set this to {@code "false"} to manage the monitor's schedule in Sentry instead.
   */
  public static final String SENTRY_UPSERT_MONITOR_CONFIG_KEY = "sentry-upsert-monitor-config";

  private static final @NotNull List<String> DAY_NAMES =
      Arrays.asList("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT");

  // Quartz ignores a step after a name, so SUN/2 is every Sunday
  private static final @NotNull Pattern NAME_STEP =
      Pattern.compile("(^|,)[A-Za-z]{3}(-[A-Za-z]{3})?/");

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
      final @Nullable MonitorConfig monitorConfig =
          shouldUpsertMonitorConfig(context) ? monitorConfigFromTrigger(context) : null;
      final @NotNull ISentryLifecycleToken lifecycleToken =
          scopes.forkedScopes("SentryJobListener").makeCurrent();
      context.put(SENTRY_SCOPE_LIFECYCLE_TOKEN_KEY, lifecycleToken);
      TracingUtils.startNewTrace(scopes);
      final @NotNull String slug = maybeSlug;
      final @NotNull CheckIn checkIn = new CheckIn(slug, CheckInStatus.IN_PROGRESS);
      checkIn.setMonitorConfig(monitorConfig);
      final @NotNull SentryId checkInId = scopes.captureCheckIn(checkIn);
      context.put(SENTRY_CHECK_IN_ID_KEY, checkInId);
      context.put(SENTRY_SLUG_KEY, slug);
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
    return o == null || !"false".equalsIgnoreCase(o.toString().trim());
  }

  private @Nullable MonitorConfig monitorConfigFromTrigger(
      final @NotNull JobExecutionContext context) {
    try {
      final @Nullable Trigger trigger = context.getTrigger();
      // Sentry can't express calendar exclusions
      if (trigger == null || trigger.getCalendarName() != null) {
        return null;
      }
      // each trigger would overwrite the others' schedule
      if (hasSeveralTriggers(context)) {
        return null;
      }
      if (trigger instanceof CronTrigger) {
        final @NotNull CronTrigger cronTrigger = (CronTrigger) trigger;
        final @Nullable String cron = toSixFieldCron(cronTrigger.getCronExpression());
        if (cron == null) {
          return null;
        }
        final @Nullable TimeZone timeZone = cronTrigger.getTimeZone();
        return MonitorConfigUtils.fromSchedule(
            cron, timeZone == null ? null : timeZone.getID(), null);
      }
      if (trigger instanceof SimpleTrigger) {
        final @NotNull SimpleTrigger simpleTrigger = (SimpleTrigger) trigger;
        if (simpleTrigger.getRepeatCount() != SimpleTrigger.REPEAT_INDEFINITELY) {
          return null;
        }
        return MonitorConfigUtils.fromSchedule(null, null, simpleTrigger.getRepeatInterval());
      }
      return null;
    } catch (Throwable e) {
      scopes
          .getOptions()
          .getLogger()
          .log(
              SentryLevel.WARNING, "Could not derive a monitor config from the Quartz trigger.", e);
      return null;
    }
  }

  private static boolean hasSeveralTriggers(final @NotNull JobExecutionContext context)
      throws SchedulerException {
    final @Nullable Scheduler scheduler = context.getScheduler();
    final @Nullable JobDetail jobDetail = context.getJobDetail();
    if (scheduler == null || jobDetail == null) {
      return false;
    }
    return scheduler.getTriggersOfJob(jobDetail.getKey()).size() > 1;
  }

  /**
   * Quartz numbers days of week 1-7 from Sunday; crontab uses 0-6. Null if a year is set or a name
   * has a step.
   */
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
    if (NAME_STEP.matcher(fields[4]).find() || NAME_STEP.matcher(fields[5]).find()) {
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
      final @Nullable String converted = toZeroBasedDayOfWeekItem(item.toUpperCase(Locale.ROOT));
      if (converted == null) {
        return null;
      }
      if (result.length() > 0) {
        result.append(',');
      }
      result.append(converted);
    }
    return result.toString();
  }

  private static @Nullable String toZeroBasedDayOfWeekItem(final @NotNull String item) {
    if (item.startsWith("*") || item.startsWith("?")) {
      // '*' starts on Sunday in both
      return item;
    }
    final int hash = item.indexOf('#');
    if (hash >= 0) {
      final @Nullable Integer day = toZeroBasedDay(item.substring(0, hash));
      return day == null ? null : day + item.substring(hash);
    }
    if (item.length() > 1 && item.endsWith("L")) {
      final @Nullable Integer day = toZeroBasedDay(item.substring(0, item.length() - 1));
      return day == null ? null : day + "L";
    }
    final int slash = item.indexOf('/');
    final @NotNull String days = slash >= 0 ? item.substring(0, slash) : item;
    final @NotNull String step = slash >= 0 ? item.substring(slash) : "";
    final @NotNull String[] range = days.split("-", -1);
    if (range.length > 2) {
      return null;
    }
    final @Nullable Integer start = toZeroBasedDay(range[0]);
    if (start == null) {
      return null;
    }
    if (range.length == 2) {
      final @Nullable Integer end = toZeroBasedDay(range[1]);
      return end == null ? null : start + "-" + end + step;
    }
    if (slash < 0 || start == 6) {
      return String.valueOf(start);
    }
    // a step from a single day ends on Saturday in Quartz, but on Sunday (7) in crontab
    return start + "-6" + step;
  }

  private static @Nullable Integer toZeroBasedDay(final @NotNull String day) {
    if (day.matches("[1-7]")) {
      return Integer.parseInt(day) - 1;
    }
    final int index = DAY_NAMES.indexOf(day);
    return index >= 0 ? index : null;
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
