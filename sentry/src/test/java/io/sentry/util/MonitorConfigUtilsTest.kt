package io.sentry.util

import com.google.common.truth.Truth.assertThat
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
    val config = MonitorConfigUtils.fromSchedule("", null, null, TimeUnit.HOURS.toMillis(2))

    assertThat(config?.schedule?.value).isEqualTo("120")
    assertThat(config?.schedule?.unit).isEqualTo("minute")
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
}
