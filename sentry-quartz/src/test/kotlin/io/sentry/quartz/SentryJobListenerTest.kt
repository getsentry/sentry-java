package io.sentry.quartz

import com.google.common.truth.Truth.assertThat
import io.sentry.CheckIn
import io.sentry.CheckInStatus
import io.sentry.IScopes
import io.sentry.ISentryLifecycleToken
import io.sentry.MonitorConfig
import io.sentry.SentryOptions
import io.sentry.protocol.SentryId
import java.util.TimeZone
import kotlin.test.BeforeTest
import kotlin.test.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.quartz.CalendarIntervalScheduleBuilder
import org.quartz.CronScheduleBuilder
import org.quartz.JobDataMap
import org.quartz.JobExecutionContext
import org.quartz.SimpleScheduleBuilder
import org.quartz.Trigger
import org.quartz.TriggerBuilder

class SentryJobListenerTest {

  private val scopes = mock<IScopes>()
  private val lifecycleToken = mock<ISentryLifecycleToken>()

  @BeforeTest
  fun setup() {
    val forkedScopes = mock<IScopes>()
    whenever(scopes.forkedScopes(any())).thenReturn(forkedScopes)
    whenever(forkedScopes.makeCurrent()).thenReturn(lifecycleToken)
    whenever(scopes.options).thenReturn(SentryOptions())
  }

  @Test
  fun `cron trigger is sent as crontab monitor config with timezone`() {
    val config =
      inProgressMonitorConfig(
        cronTrigger("0 15 10 ? * MON-FRI", TimeZone.getTimeZone("America/New_York"))
      )

    assertThat(config).isNotNull()
    assertThat(config!!.schedule.type).isEqualTo("crontab")
    assertThat(config.schedule.value).isEqualTo("15 10 * * MON-FRI")
    assertThat(config.timezone).isEqualTo("America/New_York")
  }

  @Test
  fun `cron trigger with wildcard year is sent as crontab`() {
    val config = inProgressMonitorConfig(cronTrigger("0 0 2 * * ? *"))

    assertThat(config?.schedule?.value).isEqualTo("0 2 * * *")
  }

  @Test
  fun `cron trigger with specific year sends no monitor config`() {
    assertThat(inProgressMonitorConfig(cronTrigger("0 0 2 * * ? 2030"))).isNull()
  }

  @Test
  fun `cron trigger with variable seconds sends no monitor config`() {
    assertThat(inProgressMonitorConfig(cronTrigger("0/30 * * * * ?"))).isNull()
  }

  @Test
  fun `cron trigger days of week are converted to start at zero`() {
    val config = inProgressMonitorConfig(cronTrigger("0 0 9 ? * 2-6"))

    assertThat(config?.schedule?.value).isEqualTo("0 9 * * 1-5")
  }

  @Test
  fun `simple trigger with whole minute interval is sent as interval monitor config`() {
    val trigger =
      TriggerBuilder.newTrigger()
        .withSchedule(SimpleScheduleBuilder.repeatMinutelyForever(5))
        .build()

    val config = inProgressMonitorConfig(trigger)

    assertThat(config).isNotNull()
    assertThat(config!!.schedule.type).isEqualTo("interval")
    assertThat(config.schedule.value).isEqualTo("5")
    assertThat(config.schedule.unit).isEqualTo("minute")
  }

  @Test
  fun `simple trigger with sub minute interval sends no monitor config`() {
    val trigger =
      TriggerBuilder.newTrigger()
        .withSchedule(SimpleScheduleBuilder.repeatSecondlyForever(90))
        .build()

    assertThat(inProgressMonitorConfig(trigger)).isNull()
  }

  @Test
  fun `simple trigger without repeat sends no monitor config`() {
    val trigger =
      TriggerBuilder.newTrigger().withSchedule(SimpleScheduleBuilder.simpleSchedule()).build()

    assertThat(inProgressMonitorConfig(trigger)).isNull()
  }

  @Test
  fun `other triggers send no monitor config`() {
    val trigger =
      TriggerBuilder.newTrigger()
        .withSchedule(
          CalendarIntervalScheduleBuilder.calendarIntervalSchedule().withIntervalInDays(1)
        )
        .build()

    assertThat(inProgressMonitorConfig(trigger)).isNull()
  }

  @Test
  fun `without upsert monitor config key sends no monitor config`() {
    assertThat(inProgressMonitorConfig(cronTrigger("0 0 2 * * ?"), JobDataMap())).isNull()
  }

  @Test
  fun `upsert monitor config false sends no monitor config`() {
    val jobDataMap = JobDataMap()
    jobDataMap[SentryJobListener.SENTRY_UPSERT_MONITOR_CONFIG_KEY] = "false"

    assertThat(inProgressMonitorConfig(cronTrigger("0 0 2 * * ?"), jobDataMap)).isNull()
  }

  @Test
  fun `upsert monitor config as boolean true sends monitor config`() {
    val jobDataMap = JobDataMap()
    jobDataMap[SentryJobListener.SENTRY_UPSERT_MONITOR_CONFIG_KEY] = true

    assertThat(inProgressMonitorConfig(cronTrigger("0 0 2 * * ?"), jobDataMap)).isNotNull()
  }

  @Test
  fun `failing trigger still sends the check-in without monitor config`() {
    val trigger = mock<org.quartz.CronTrigger>()
    whenever(trigger.cronExpression).thenThrow(IllegalStateException("boom"))

    assertThat(inProgressMonitorConfig(trigger)).isNull()
  }

  @Test
  fun `quartz cron is converted to six fields`() {
    val expected =
      mapOf(
        "0 0 12 * * ?" to "0 0 12 * * ?",
        "0 0 12 * * ? *" to "0 0 12 * * ?",
        "0 0 12 ? * 1,7" to "0 0 12 ? * 0,6",
        "0 0 12 ? * SUN,sat" to "0 0 12 ? * SUN,SAT",
        "0 0 12 ? * 6#3" to "0 0 12 ? * 5#3",
        "0 0 12 ? * 6L" to "0 0 12 ? * 5L",
        "0 0 12 ? * 1/2" to "0 0 12 ? * 0/2",
        "0 0 12 L * ?" to "0 0 12 L * ?",
        "0 0 12 15W * ?" to "0 0 12 15W * ?",
      )
    for ((quartz, cron) in expected) {
      assertThat(SentryJobListener.toSixFieldCron(quartz)).isEqualTo(cron)
    }
  }

  @Test
  fun `quartz cron that cannot be converted returns null`() {
    for (quartz in listOf("0 0 12 * * ? 2030", "0 0 12 ? * L", "0 0 12 ? * 8", "0 12 * * *")) {
      assertThat(SentryJobListener.toSixFieldCron(quartz)).isNull()
    }
  }

  private fun cronTrigger(cron: String, timeZone: TimeZone = TimeZone.getTimeZone("UTC")): Trigger =
    TriggerBuilder.newTrigger()
      .withSchedule(CronScheduleBuilder.cronSchedule(cron).inTimeZone(timeZone))
      .build()

  private fun upsertJobDataMap(): JobDataMap {
    val jobDataMap = JobDataMap()
    jobDataMap[SentryJobListener.SENTRY_UPSERT_MONITOR_CONFIG_KEY] = "true"
    return jobDataMap
  }

  private fun inProgressMonitorConfig(
    trigger: Trigger,
    jobDataMap: JobDataMap = upsertJobDataMap(),
  ): MonitorConfig? {
    jobDataMap[SentryJobListener.SENTRY_SLUG_KEY] = "my-job"
    val context = mock<JobExecutionContext>()
    whenever(context.mergedJobDataMap).thenReturn(jobDataMap)
    whenever(context.trigger).thenReturn(trigger)
    val checkInCaptor = argumentCaptor<CheckIn>()
    whenever(scopes.captureCheckIn(checkInCaptor.capture())).thenReturn(SentryId())

    SentryJobListener(scopes).jobToBeExecuted(context)

    assertThat(checkInCaptor.allValues).hasSize(1)
    assertThat(checkInCaptor.firstValue.monitorSlug).isEqualTo("my-job")
    assertThat(checkInCaptor.firstValue.status).isEqualTo(CheckInStatus.IN_PROGRESS.apiName())
    return checkInCaptor.firstValue.monitorConfig
  }
}
