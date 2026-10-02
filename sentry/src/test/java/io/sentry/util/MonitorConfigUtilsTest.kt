package io.sentry.util

import com.google.common.truth.Truth.assertThat
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.test.Test

class MonitorConfigUtilsTest {

  @Test
  fun `cron with fixed seconds drops the seconds field`() {
    val config = MonitorConfigUtils.fromSchedule("0 15 10 * * MON-FRI", null, null, null)

    assertThat(config).isNotNull()
    assertThat(config!!.schedule.type).isEqualTo("crontab")
    assertThat(config.schedule.value).isEqualTo("15 10 * * MON-FRI")
    assertThat(config.schedule.unit).isNull()
    assertThat(config.timezone).isNull()
  }

  @Test
  fun `cron whitespace is normalized`() {
    val config = MonitorConfigUtils.fromSchedule("  30   0 2 * * *  ", null, null, null)

    assertThat(config?.schedule?.value).isEqualTo("0 2 * * *")
  }

  @Test
  fun `cron question mark is converted to asterisk`() {
    val config = MonitorConfigUtils.fromSchedule("0 0 9 ? * MON", null, null, null)

    assertThat(config?.schedule?.value).isEqualTo("0 9 * * MON")
  }

  @Test
  fun `cron macros are expanded`() {
    val expected =
      mapOf(
        "@yearly" to "0 0 1 1 *",
        "@annually" to "0 0 1 1 *",
        "@monthly" to "0 0 1 * *",
        "@weekly" to "0 0 * * 0",
        "@daily" to "0 0 * * *",
        "@midnight" to "0 0 * * *",
        "@hourly" to "0 * * * *",
        "@HOURLY" to "0 * * * *",
      )
    for ((macro, crontab) in expected) {
      assertThat(MonitorConfigUtils.fromSchedule(macro, null, null, null)?.schedule?.value)
        .isEqualTo(crontab)
    }
  }

  @Test
  fun `unknown cron macro returns null`() {
    assertThat(MonitorConfigUtils.fromSchedule("@reboot", null, null, null)).isNull()
  }

  @Test
  fun `cron with variable seconds returns null`() {
    assertThat(MonitorConfigUtils.fromSchedule("*/30 * * * * *", null, null, null)).isNull()
    assertThat(MonitorConfigUtils.fromSchedule("0,30 * * * * *", null, null, null)).isNull()
    assertThat(MonitorConfigUtils.fromSchedule("0-10 * * * * *", null, null, null)).isNull()
  }

  @Test
  fun `cron without 6 fields returns null`() {
    assertThat(MonitorConfigUtils.fromSchedule("0 * * * *", null, null, null)).isNull()
    assertThat(MonitorConfigUtils.fromSchedule("0 0 * * * * 2030", null, null, null)).isNull()
  }

  @Test
  fun `cron with both day of month and day of week returns null`() {
    for (cron in listOf("0 0 9 1-7 * MON", "0 0 9 15 * 1-5", "0 0 9 L * 5L", "0 0 9 1 * 1/2")) {
      assertThat(MonitorConfigUtils.fromSchedule(cron, null, null, null)).isNull()
    }
  }

  @Test
  fun `cron with a day field starting with asterisk is kept`() {
    val expected =
      mapOf(
        "0 0 9 */2 * MON" to "0 9 */2 * MON",
        "0 0 9 15 * */2" to "0 9 15 * */2",
        "0 0 9 1 * ?" to "0 9 1 * *",
      )
    for ((cron, crontab) in expected) {
      assertThat(MonitorConfigUtils.fromSchedule(cron, null, null, null)?.schedule?.value)
        .isEqualTo(crontab)
    }
  }

  @Test
  fun `cron syntax Sentry accepts is kept`() {
    val expected =
      listOf(
        "0 */15 * * * *",
        "0 5/15 * * * *",
        "0 0,30 8-18/2 * * *",
        "0 0 0 1,15 JAN-MAR,dec *",
        "0 0 0 L * *",
        "0 0 0 lw * *",
        "0 0 0 31 1-12 *",
        "0 0 0 * * 0,7",
        "0 0 0 * * sat",
        "0 0 0 * * MON-FRI/2",
        "0 0 0 * * 5L",
        "0 0 0 * * 5#3",
        "0 0 0 * * MON#2",
        "0 0 0 * * 1-7/2",
      )
    for (cron in expected) {
      assertThat(MonitorConfigUtils.fromSchedule(cron, null, null, null)?.schedule?.value)
        .isEqualTo(cron.substringAfter(' '))
    }
  }

  @Test
  fun `cron syntax Sentry rejects returns null`() {
    val rejected =
      listOf(
        "60 * * * * *",
        "0 60 * * * *",
        "0 0 24 * * *",
        "0 0 22-2 * * *",
        "0 */0 * * * *",
        "0 0,,30 * * * *",
        "0 ? * * * *",
        "0 0 0 0 * *",
        "0 0 0 32 * *",
        "0 0 0 15W * *",
        "0 0 0 LW/2 * *",
        "0 0 0 L-3 * *",
        "0 0 0 5L * *",
        "0 0 0 1#2 * *",
        "0 0 0 30 2 *",
        "0 0 0 31 4,6 *",
        "0 0 0 * 0 *",
        "0 0 0 * 13 *",
        "0 0 0 * FOO *",
        "0 0 0 * * 8",
        "0 0 0 * * SAT-SUN",
        "0 0 0 * * L",
        "0 0 0 * * FRIL",
        "0 0 0 * * L5",
        "0 0 0 * * 5#6",
        "0 0 0 * * 5#0",
      )
    for (cron in rejected) {
      assertThat(MonitorConfigUtils.fromSchedule(cron, null, null, null)).isNull()
    }
  }

  @Test
  fun `stepped single value range returns null`() {
    // Spring and Quartz run only at the value, cronsim steps to the field max
    for (cron in
      listOf("0 10-10/2 * * * *", "0 0 0-0/3 * * *", "0 0 0 ? * 4-4/3", "0 0 0 * 6-6/1 *")) {
      assertThat(MonitorConfigUtils.fromSchedule(cron, null, null, null)).isNull()
    }
  }

  @Test
  fun `disabled cron returns null`() {
    assertThat(MonitorConfigUtils.fromSchedule("-", null, null, null)).isNull()
  }

  @Test
  fun `zone is set for cron`() {
    val config = MonitorConfigUtils.fromSchedule("0 0 2 * * *", "Europe/Vienna", null, null)

    assertThat(config?.schedule?.value).isEqualTo("0 2 * * *")
    assertThat(config?.timezone).isEqualTo("Europe/Vienna")
  }

  @Test
  fun `IANA zones are kept`() {
    for (zone in
      listOf("UTC", "GMT", "Etc/GMT+5", "US/Eastern", "America/Argentina/Buenos_Aires")) {
      assertThat(MonitorConfigUtils.fromSchedule("0 0 2 * * *", zone, null, null)?.timezone)
        .isEqualTo(zone)
    }
  }

  @Test
  fun `whole hour fixed offsets are converted to Etc zones`() {
    val expected =
      mapOf(
        "GMT+2" to "Etc/GMT-2",
        "GMT+02:00" to "Etc/GMT-2",
        "UTC-5" to "Etc/GMT+5",
        "+03:00" to "Etc/GMT-3",
        "-1200" to "Etc/GMT+12",
        "GMT+14" to "Etc/GMT-14",
        "GMT-00:00" to "Etc/GMT",
      )
    for ((zone, timezone) in expected) {
      assertThat(MonitorConfigUtils.fromSchedule("0 0 2 * * *", zone, null, null)?.timezone)
        .isEqualTo(timezone)
    }
  }

  @Test
  fun `zones Sentry rejects return null`() {
    for (zone in
      listOf("GMT+05:30", "GMT+15", "UTC-13", "PST", "IST", "Nowhere/Land", "SystemV/EST5")) {
      assertThat(MonitorConfigUtils.fromSchedule("0 0 2 * * *", zone, null, null)).isNull()
    }
  }

  @Test
  fun `empty zone is not set`() {
    assertThat(MonitorConfigUtils.fromSchedule("0 0 2 * * *", "", null, null)?.timezone).isNull()
  }

  @Test
  fun `fixed rate in whole minutes is an interval`() {
    val config = MonitorConfigUtils.fromSchedule(null, null, TimeUnit.MINUTES.toMillis(5), null)

    assertThat(config).isNotNull()
    assertThat(config!!.schedule.type).isEqualTo("interval")
    assertThat(config.schedule.value).isEqualTo("5")
    assertThat(config.schedule.unit).isEqualTo("minute")
  }

  @Test
  fun `fixed delay in whole minutes is an interval`() {
    val config = MonitorConfigUtils.fromSchedule("", null, null, TimeUnit.MINUTES.toMillis(90))

    assertThat(config?.schedule?.value).isEqualTo("90")
    assertThat(config?.schedule?.unit).isEqualTo("minute")
  }

  @Test
  fun `period in whole hours or days uses the largest unit`() {
    val expected =
      mapOf(
        TimeUnit.MINUTES.toMillis(60) to ("1" to "hour"),
        TimeUnit.HOURS.toMillis(36) to ("36" to "hour"),
        TimeUnit.HOURS.toMillis(24) to ("1" to "day"),
        TimeUnit.DAYS.toMillis(14) to ("14" to "day"),
      )
    for ((millis, interval) in expected) {
      val config = MonitorConfigUtils.fromSchedule(null, null, millis, null)
      assertThat(config?.schedule?.value).isEqualTo(interval.first)
      assertThat(config?.schedule?.unit).isEqualTo(interval.second)
    }
  }

  @Test
  fun `zone is ignored for intervals`() {
    val config =
      MonitorConfigUtils.fromSchedule(null, "Europe/Vienna", TimeUnit.MINUTES.toMillis(1), null)

    assertThat(config?.schedule?.value).isEqualTo("1")
    assertThat(config?.timezone).isNull()
  }

  @Test
  fun `sub-minute period returns null`() {
    assertThat(MonitorConfigUtils.fromSchedule(null, null, 30_000L, null)).isNull()
    assertThat(MonitorConfigUtils.fromSchedule(null, null, 0L, null)).isNull()
  }

  @Test
  fun `period not in whole minutes returns null`() {
    assertThat(MonitorConfigUtils.fromSchedule(null, null, 90_000L, null)).isNull()
  }

  @Test
  fun `period too large for an int returns null`() {
    assertThat(
        MonitorConfigUtils.fromSchedule(
          null,
          null,
          TimeUnit.MINUTES.toMillis(Int.MAX_VALUE.toLong() + 1),
          null,
        )
      )
      .isNull()
  }

  @Test
  fun `more than one schedule kind returns null`() {
    assertThat(MonitorConfigUtils.fromSchedule("0 0 2 * * *", null, 60_000L, null)).isNull()
    assertThat(MonitorConfigUtils.fromSchedule(null, null, 60_000L, 60_000L)).isNull()
  }

  @Test
  fun `no schedule returns null`() {
    assertThat(MonitorConfigUtils.fromSchedule(null, null, null, null)).isNull()
    assertThat(MonitorConfigUtils.fromSchedule("", null, null, null)).isNull()
  }

  @Test
  fun `Spring days of week are rewritten to how crontab reads them`() {
    val expected =
      mapOf(
        "0 15 10 * * MON-FRI" to "15 10 * * 1-5",
        "0 0 9 * * */2" to "0 9 * * 1-7/2",
        "0 0 9 ? * SUN/2" to "0 9 * * 7/2",
        "0 0 9 ? * SUN-TUE,fri-sun" to "0 9 * * 0-2,5-7",
        "0 0 9 ? * 7-2" to "0 9 * * 0-2",
        "0 0 9 ? * 0,7" to "0 9 * * 0,7",
        "0 0 9 ? * FRIL" to "0 9 * * 5L",
        "0 0 9 ? * MON#2" to "0 9 * * 1#2",
        "0 0 9 * * *" to "0 9 * * *",
      )
    for ((cron, crontab) in expected) {
      assertThat(MonitorConfigUtils.fromSpringScheduled(cron, "UTC", null, null)?.schedule?.value)
        .isEqualTo(crontab)
    }
  }

  @Test
  fun `Spring cron without zone uses the JVM default zone`() {
    val defaultTimeZone = TimeZone.getDefault()
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
    try {
      assertThat(MonitorConfigUtils.fromSpringScheduled("0 0 2 * * *", null, null, null)?.timezone)
        .isEqualTo("Asia/Tokyo")
      assertThat(MonitorConfigUtils.fromSpringScheduled("0 0 2 * * *", "", null, null)?.timezone)
        .isEqualTo("Asia/Tokyo")
    } finally {
      TimeZone.setDefault(defaultTimeZone)
    }
  }

  @Test
  fun `Spring cron zone is converted`() {
    val config = MonitorConfigUtils.fromSpringScheduled("0 0 2 * * *", "GMT+2", null, null)

    assertThat(config?.schedule?.value).isEqualTo("0 2 * * *")
    assertThat(config?.timezone).isEqualTo("Etc/GMT-2")
  }

  @Test
  fun `Spring schedules Sentry can't express return null`() {
    for (cron in listOf("0 0 9 1-7 * MON", "-", "\${my.cron.missing}", "*/30 * * * * *")) {
      assertThat(MonitorConfigUtils.fromSpringScheduled(cron, "UTC", null, null)).isNull()
    }
    assertThat(MonitorConfigUtils.fromSpringScheduled("0 0 2 * * *", "GMT+05:30", null, null))
      .isNull()
    assertThat(MonitorConfigUtils.fromSpringScheduled(null, null, 30_000L, null)).isNull()
  }

  @Test
  fun `Spring periods are intervals without zone`() {
    val rate = MonitorConfigUtils.fromSpringScheduled("", null, TimeUnit.MINUTES.toMillis(5), null)
    assertThat(rate?.schedule?.type).isEqualTo("interval")
    assertThat(rate?.schedule?.value).isEqualTo("5")
    assertThat(rate?.schedule?.unit).isEqualTo("minute")
    assertThat(rate?.timezone).isNull()

    val delay = MonitorConfigUtils.fromSpringScheduled(null, null, null, TimeUnit.HOURS.toMillis(2))
    assertThat(delay?.schedule?.value).isEqualTo("2")
    assertThat(delay?.schedule?.unit).isEqualTo("hour")
  }

  @Test
  fun `parsePeriodMillis parses plain numbers in the default unit`() {
    assertThat(MonitorConfigUtils.parsePeriodMillis("300000", TimeUnit.MILLISECONDS))
      .isEqualTo(300_000L)
    assertThat(MonitorConfigUtils.parsePeriodMillis(" 5 ", TimeUnit.MINUTES)).isEqualTo(300_000L)
  }

  @Test
  fun `parsePeriodMillis parses the simple duration style`() {
    val expected =
      mapOf(
        "2000000ns" to 2L,
        "3000us" to 3L,
        "500ms" to 500L,
        "30s" to 30_000L,
        "5m" to 300_000L,
        "1h" to 3_600_000L,
        "1d" to 86_400_000L,
        "5M" to 300_000L,
        "+5m" to 300_000L,
      )
    for ((value, millis) in expected) {
      assertThat(MonitorConfigUtils.parsePeriodMillis(value, TimeUnit.SECONDS)).isEqualTo(millis)
    }
  }

  @Test
  fun `parsePeriodMillis returns null for unparseable values`() {
    for (value in listOf("", "abc", "5x", "5 m", "1.5m", "PT5M", "99999999999999999999")) {
      assertThat(MonitorConfigUtils.parsePeriodMillis(value, TimeUnit.MILLISECONDS)).isNull()
    }
    assertThat(MonitorConfigUtils.parsePeriodMillis(null, TimeUnit.MILLISECONDS)).isNull()
  }
}
