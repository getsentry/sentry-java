package io.sentry.quartz;

import io.sentry.BuildConfig;
import io.sentry.CheckIn;
import io.sentry.CheckInStatus;
import io.sentry.HubAdapter;
import io.sentry.IHub;
import io.sentry.MonitorConfig;
import io.sentry.MonitorSchedule;
import io.sentry.MonitorScheduleUnit;
import io.sentry.Sentry;
import io.sentry.SentryIntegrationPackageStorage;
import io.sentry.SentryLevel;
import io.sentry.protocol.SentryId;
import io.sentry.util.Objects;
import java.util.List;
import java.util.TimeZone;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.quartz.CalendarIntervalTrigger;
import org.quartz.CronTrigger;
import org.quartz.DateBuilder;
import org.quartz.Job;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.JobListener;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;

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
  public void jobToBeExecuted(JobExecutionContext context) {
    try {
      if (isDisabled()) {
        return;
      }
      final @NotNull String slug = getSlug(context.getJobDetail());
      final @NotNull CheckIn checkIn = new CheckIn(slug, CheckInStatus.IN_PROGRESS);

      final @Nullable MonitorConfig monitorConfig = extractMonitorConfig(context);
      if (monitorConfig != null) {
        checkIn.setMonitorConfig(monitorConfig);
      }

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

  private @NotNull String getSlug(final @Nullable JobDetail jobDetail) {
    if (jobDetail == null) {
      return "fallback";
    }
    final @NotNull StringBuilder slugBuilder = new StringBuilder();

    final @Nullable JobKey key = jobDetail.getKey();
    if (key != null) {
      slugBuilder.append(key.getName());
    }

    slugBuilder.append("__");

    final @Nullable Class<? extends Job> jobClass = jobDetail.getJobClass();
    if (jobClass != null) {
      slugBuilder.append(jobClass.getCanonicalName());
    }

    return slugBuilder.toString();
  }

  private @Nullable MonitorConfig extractMonitorConfig(final @NotNull JobExecutionContext context) {
    @Nullable MonitorSchedule schedule = null;
    @Nullable String cronExpression = null;
    @Nullable TimeZone timeZone = TimeZone.getDefault();
    @Nullable Integer repeatInterval = null;
    @Nullable MonitorScheduleUnit timeUnit = null;

    try {
      List<? extends Trigger> triggersOfJob =
          context.getScheduler().getTriggersOfJob(context.getTrigger().getJobKey());
      for (Trigger trigger : triggersOfJob) {
        if (trigger instanceof CronTrigger) {
          final CronTrigger cronTrigger = (CronTrigger) trigger;
          cronExpression = cronTrigger.getCronExpression();
          timeZone = cronTrigger.getTimeZone();
        } else if (trigger instanceof SimpleTrigger) {
          final SimpleTrigger simpleTrigger = (SimpleTrigger) trigger;
          long tmpRepeatInterval = simpleTrigger.getRepeatInterval();
          repeatInterval = millisToMinutes(Double.valueOf(tmpRepeatInterval));
          timeUnit = MonitorScheduleUnit.MINUTE;
        } else if (trigger instanceof CalendarIntervalTrigger) {
          final CalendarIntervalTrigger calendarIntervalTrigger = (CalendarIntervalTrigger) trigger;
          DateBuilder.IntervalUnit repeatIntervalUnit =
              calendarIntervalTrigger.getRepeatIntervalUnit();
          int tmpRepeatInterval = calendarIntervalTrigger.getRepeatInterval();
          if (DateBuilder.IntervalUnit.SECOND.equals(repeatIntervalUnit)) {
            repeatInterval = secondsToMinutes(Double.valueOf(tmpRepeatInterval));
            timeUnit = MonitorScheduleUnit.MINUTE;
          } else if (DateBuilder.IntervalUnit.MILLISECOND.equals(repeatIntervalUnit)) {
            repeatInterval = millisToMinutes(Double.valueOf(tmpRepeatInterval));
            timeUnit = MonitorScheduleUnit.MINUTE;
          } else {
            repeatInterval = tmpRepeatInterval;
            timeUnit = convertUnit(repeatIntervalUnit);
          }
        }
      }
    } catch (Throwable t) {
      Sentry.getCurrentHub()
          .getOptions()
          .getLogger()
          .log(SentryLevel.ERROR, "Unable to extract monitor config for check-in.", t);
    }
    if (cronExpression != null) {
      schedule = MonitorSchedule.crontab(cronExpression);
    } else if (repeatInterval != null && timeUnit != null) {
      schedule = MonitorSchedule.interval(repeatInterval.intValue(), timeUnit);
    }

    if (schedule != null) {
      final @Nullable MonitorConfig monitorConfig = new MonitorConfig(schedule);
      if (timeZone != null) {
        monitorConfig.setTimezone(timeZone.getID());
      }
      return monitorConfig;
    } else {
      return null;
    }
  }

  private @Nullable Integer millisToMinutes(final @NotNull Double milis) {
    return Double.valueOf((milis / 1000.0) / 60.0).intValue();
  }

  private @Nullable Integer secondsToMinutes(final @NotNull Double seconds) {
    return Double.valueOf(seconds / 60.0).intValue();
  }

  private @Nullable MonitorScheduleUnit convertUnit(
      final @Nullable DateBuilder.IntervalUnit intervalUnit) {
    if (intervalUnit == null) {
      return null;
    }

    if (DateBuilder.IntervalUnit.MINUTE.equals(intervalUnit)) {
      return MonitorScheduleUnit.MINUTE;
    } else if (DateBuilder.IntervalUnit.HOUR.equals(intervalUnit)) {
      return MonitorScheduleUnit.HOUR;
    } else if (DateBuilder.IntervalUnit.DAY.equals(intervalUnit)) {
      return MonitorScheduleUnit.DAY;
    } else if (DateBuilder.IntervalUnit.WEEK.equals(intervalUnit)) {
      return MonitorScheduleUnit.WEEK;
    } else if (DateBuilder.IntervalUnit.MONTH.equals(intervalUnit)) {
      return MonitorScheduleUnit.MONTH;
    } else if (DateBuilder.IntervalUnit.YEAR.equals(intervalUnit)) {
      return MonitorScheduleUnit.YEAR;
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
