package io.sentry.spring

import io.sentry.CheckIn
import io.sentry.CheckInStatus
import io.sentry.IScopes
import io.sentry.ISentryLifecycleToken
import io.sentry.MonitorConfig
import io.sentry.Sentry
import io.sentry.SentryOptions
import io.sentry.protocol.SentryId
import io.sentry.spring.checkin.SentryCheckIn
import io.sentry.spring.checkin.SentryCheckInAdviceConfiguration
import io.sentry.spring.checkin.SentryCheckInPointcutConfiguration
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.RuntimeException
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import org.junit.jupiter.api.assertThrows
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.context.annotation.Import
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.util.StringValueResolver

@RunWith(SpringRunner::class)
@SpringJUnitConfig(SentryCheckInAdviceTest.Config::class)
@TestPropertySource(
  properties =
    [
      "my.cron.slug = mypropertycronslug",
      "my.cron.schedule = 0 30 2 * * *",
      "my.cron.zone = America/New_York",
      "my.cron.delay = PT10M",
    ]
)
class SentryCheckInAdviceTest {

  @Autowired lateinit var sampleService: SampleService

  @Autowired lateinit var sampleServiceNoSlug: SampleServiceNoSlug

  @Autowired lateinit var sampleServiceHeartbeat: SampleServiceHeartbeat

  @Autowired lateinit var sampleServiceSpringProperties: SampleServiceSpringProperties

  @Autowired lateinit var sampleServiceScheduled: SampleServiceScheduled

  @Autowired lateinit var scopes: IScopes

  val lifecycleToken = mock<ISentryLifecycleToken>()

  @BeforeTest
  fun setup() {
    reset(scopes)
    whenever(scopes.options).thenReturn(SentryOptions())
    whenever(scopes.forkedScopes(any())).thenReturn(scopes)
    whenever(scopes.makeCurrent()).thenReturn(lifecycleToken)
  }

  @Test
  fun `when method is annotated with @SentryCheckIn, every method call creates two check-ins`() {
    val checkInId = SentryId()
    val checkInCaptor = argumentCaptor<CheckIn>()
    whenever(scopes.captureCheckIn(checkInCaptor.capture())).thenReturn(checkInId)
    val result = sampleService.hello()
    assertEquals(1, result)
    assertEquals(2, checkInCaptor.allValues.size)

    val inProgressCheckIn = checkInCaptor.firstValue
    assertEquals("monitor_slug_1", inProgressCheckIn.monitorSlug)
    assertEquals(CheckInStatus.IN_PROGRESS.apiName(), inProgressCheckIn.status)
    assertNull(inProgressCheckIn.monitorConfig)

    val doneCheckIn = checkInCaptor.lastValue
    assertEquals("monitor_slug_1", doneCheckIn.monitorSlug)
    assertEquals(CheckInStatus.OK.apiName(), doneCheckIn.status)

    val order = inOrder(scopes, lifecycleToken)
    order.verify(scopes).forkedScopes(any())
    order.verify(scopes).makeCurrent()
    order.verify(scopes, times(2)).captureCheckIn(any())
    order.verify(lifecycleToken).close()
  }

  @Test
  fun `when method is annotated with @SentryCheckIn, every method call creates two check-ins error`() {
    val checkInId = SentryId()
    val checkInCaptor = argumentCaptor<CheckIn>()
    whenever(scopes.captureCheckIn(checkInCaptor.capture())).thenReturn(checkInId)
    assertThrows<RuntimeException> { sampleService.oops() }
    assertEquals(2, checkInCaptor.allValues.size)

    val inProgressCheckIn = checkInCaptor.firstValue
    assertEquals("monitor_slug_1e", inProgressCheckIn.monitorSlug)
    assertEquals(CheckInStatus.IN_PROGRESS.apiName(), inProgressCheckIn.status)

    val doneCheckIn = checkInCaptor.lastValue
    assertEquals("monitor_slug_1e", doneCheckIn.monitorSlug)
    assertEquals(CheckInStatus.ERROR.apiName(), doneCheckIn.status)

    val order = inOrder(scopes, lifecycleToken)
    order.verify(scopes).forkedScopes(any())
    order.verify(scopes).makeCurrent()
    order.verify(scopes, times(2)).captureCheckIn(any())
    order.verify(lifecycleToken).close()
  }

  @Test
  fun `when method is annotated with @SentryCheckIn and heartbeat only, every method call creates only one check-in at the end`() {
    val checkInId = SentryId()
    val checkInCaptor = argumentCaptor<CheckIn>()
    whenever(scopes.captureCheckIn(checkInCaptor.capture())).thenReturn(checkInId)
    val result = sampleServiceHeartbeat.hello()
    assertEquals(1, result)
    assertEquals(1, checkInCaptor.allValues.size)

    val doneCheckIn = checkInCaptor.lastValue
    assertEquals("monitor_slug_2", doneCheckIn.monitorSlug)
    assertEquals(CheckInStatus.OK.apiName(), doneCheckIn.status)
    assertNotNull(doneCheckIn.duration)

    val order = inOrder(scopes, lifecycleToken)
    order.verify(scopes).forkedScopes(any())
    order.verify(scopes).makeCurrent()
    order.verify(scopes).captureCheckIn(any())
    order.verify(lifecycleToken).close()
  }

  @Test
  fun `when method is annotated with @SentryCheckIn and heartbeat only, every method call creates only one check-in at the end with error`() {
    val checkInId = SentryId()
    val checkInCaptor = argumentCaptor<CheckIn>()
    whenever(scopes.captureCheckIn(checkInCaptor.capture())).thenReturn(checkInId)
    assertThrows<RuntimeException> { sampleServiceHeartbeat.oops() }
    assertEquals(1, checkInCaptor.allValues.size)

    val doneCheckIn = checkInCaptor.lastValue
    assertEquals("monitor_slug_2e", doneCheckIn.monitorSlug)
    assertEquals(CheckInStatus.ERROR.apiName(), doneCheckIn.status)
    assertNotNull(doneCheckIn.duration)

    val order = inOrder(scopes, lifecycleToken)
    order.verify(scopes).forkedScopes(any())
    order.verify(scopes).makeCurrent()
    order.verify(scopes).captureCheckIn(any())
    order.verify(lifecycleToken).close()
  }

  @Test
  fun `when method is annotated with @SentryCheckIn but slug is missing, does not create check-in`() {
    val checkInId = SentryId()
    val checkInCaptor = argumentCaptor<CheckIn>()
    whenever(scopes.captureCheckIn(checkInCaptor.capture())).thenReturn(checkInId)
    val result = sampleServiceNoSlug.hello()
    assertEquals(1, result)
    assertEquals(0, checkInCaptor.allValues.size)

    verify(scopes, never()).forkedScopes(any())
    verify(scopes, never()).makeCurrent()
    verify(scopes, never()).captureCheckIn(any())
    verify(lifecycleToken, never()).close()
  }

  @Test
  fun `when @SentryCheckIn is passed a spring property it is resolved correctly`() {
    val checkInId = SentryId()
    val checkInCaptor = argumentCaptor<CheckIn>()
    whenever(scopes.captureCheckIn(checkInCaptor.capture())).thenReturn(checkInId)
    val result = sampleServiceSpringProperties.hello()
    assertEquals(1, result)
    assertEquals(1, checkInCaptor.allValues.size)

    val doneCheckIn = checkInCaptor.lastValue
    assertEquals("mypropertycronslug", doneCheckIn.monitorSlug)
    assertEquals(CheckInStatus.OK.apiName(), doneCheckIn.status)
    assertNotNull(doneCheckIn.duration)

    val order = inOrder(scopes, lifecycleToken)
    order.verify(scopes).forkedScopes(any())
    order.verify(scopes).makeCurrent()
    order.verify(scopes).captureCheckIn(any())
    order.verify(lifecycleToken).close()
  }

  @Test
  fun `when @SentryCheckIn is passed a spring property that does not exist, raw value is used`() {
    val checkInId = SentryId()
    val checkInCaptor = argumentCaptor<CheckIn>()
    whenever(scopes.captureCheckIn(checkInCaptor.capture())).thenReturn(checkInId)
    val result = sampleServiceSpringProperties.helloUnresolvedProperty()
    assertEquals(1, result)
    assertEquals(1, checkInCaptor.allValues.size)

    val doneCheckIn = checkInCaptor.lastValue
    assertEquals("\${my.cron.unresolved.property}", doneCheckIn.monitorSlug)
    assertEquals(CheckInStatus.OK.apiName(), doneCheckIn.status)
    assertNotNull(doneCheckIn.duration)

    val order = inOrder(scopes, lifecycleToken)
    order.verify(scopes).forkedScopes(any())
    order.verify(scopes).makeCurrent()
    order.verify(scopes).captureCheckIn(any())
    order.verify(lifecycleToken).close()
  }

  @Test
  fun `when @SentryCheckIn is passed a spring property that causes an exception, raw value is used`() {
    val checkInId = SentryId()
    val checkInCaptor = argumentCaptor<CheckIn>()
    whenever(scopes.captureCheckIn(checkInCaptor.capture())).thenReturn(checkInId)
    val result = sampleServiceSpringProperties.helloExceptionProperty()
    assertEquals(1, result)
    assertEquals(1, checkInCaptor.allValues.size)

    val doneCheckIn = checkInCaptor.lastValue
    assertEquals("\${my.cron.exception.property}", doneCheckIn.monitorSlug)
    assertEquals(CheckInStatus.OK.apiName(), doneCheckIn.status)
    assertNotNull(doneCheckIn.duration)

    val order = inOrder(scopes, lifecycleToken)
    order.verify(scopes).forkedScopes(any())
    order.verify(scopes).makeCurrent()
    order.verify(scopes).captureCheckIn(any())
    order.verify(lifecycleToken).close()
  }

  @Test
  fun `cron with fixed seconds is sent as crontab monitor config in the JVM default zone`() {
    val defaultTimeZone = TimeZone.getDefault()
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
    try {
      val config = inProgressMonitorConfig { sampleServiceScheduled.cron() }
      assertNotNull(config)
      assertEquals("crontab", config.schedule.type)
      assertEquals("15 10 * * MON-FRI", config.schedule.value)
      assertNull(config.schedule.unit)
      assertEquals("Asia/Tokyo", config.timezone)
    } finally {
      TimeZone.setDefault(defaultTimeZone)
    }
  }

  @Test
  fun `cron and zone placeholders are resolved`() {
    val config = inProgressMonitorConfig { sampleServiceScheduled.cronFromProperties() }
    assertEquals("30 2 * * *", config?.schedule?.value)
    assertEquals("America/New_York", config?.timezone)
  }

  @Test
  fun `fixed rate in whole minutes is sent as interval monitor config`() {
    val config = inProgressMonitorConfig { sampleServiceScheduled.fixedRateMinutes() }
    assertNotNull(config)
    assertEquals("interval", config.schedule.type)
    assertEquals("5", config.schedule.value)
    assertEquals("minute", config.schedule.unit)
  }

  @Test
  fun `fixed delay with time unit is sent as interval monitor config`() {
    val config = inProgressMonitorConfig { sampleServiceScheduled.fixedDelayHours() }
    assertEquals("120", config?.schedule?.value)
    assertEquals("minute", config?.schedule?.unit)
  }

  @Test
  fun `fixed delay string placeholder is resolved`() {
    val config = inProgressMonitorConfig { sampleServiceScheduled.fixedDelayFromProperties() }
    assertEquals("10", config?.schedule?.value)
    assertEquals("minute", config?.schedule?.unit)
  }

  @Test
  fun `fixed rate string in simple duration style is sent as interval monitor config`() {
    val config = inProgressMonitorConfig { sampleServiceScheduled.fixedRateSimpleStyle() }
    assertEquals("5", config?.schedule?.value)
    assertEquals("minute", config?.schedule?.unit)
  }

  @Test
  fun `unparseable fixed rate string sends no monitor config`() {
    assertNull(inProgressMonitorConfig { sampleServiceScheduled.fixedRateUnparseable() })
  }

  @Test
  fun `monitor config is derived once per method`() {
    val first = inProgressMonitorConfig { sampleServiceScheduled.fixedRateMinutes() }
    val second = inProgressMonitorConfig { sampleServiceScheduled.fixedRateMinutes() }
    assertNotNull(first)
    assertSame(first, second)
  }

  @Test
  fun `multiple schedules send no monitor config`() {
    assertNull(inProgressMonitorConfig { sampleServiceScheduled.multipleSchedules() })
  }

  @Test
  fun `upsertMonitorConfig false sends no monitor config`() {
    assertNull(inProgressMonitorConfig { sampleServiceScheduled.optOut() })
  }

  @Test
  fun `heartbeat with @Scheduled sends a single check-in without monitor config`() {
    val checkInCaptor = argumentCaptor<CheckIn>()
    whenever(scopes.captureCheckIn(checkInCaptor.capture())).thenReturn(SentryId())
    sampleServiceScheduled.heartbeat()
    assertEquals(1, checkInCaptor.allValues.size)
    assertEquals(CheckInStatus.OK.apiName(), checkInCaptor.firstValue.status)
    assertNull(checkInCaptor.firstValue.monitorConfig)
  }

  private fun inProgressMonitorConfig(block: () -> Unit): MonitorConfig? {
    val checkInCaptor = argumentCaptor<CheckIn>()
    whenever(scopes.captureCheckIn(checkInCaptor.capture())).thenReturn(SentryId())
    block()
    assertEquals(2, checkInCaptor.allValues.size)
    assertEquals(CheckInStatus.IN_PROGRESS.apiName(), checkInCaptor.firstValue.status)
    assertNull(checkInCaptor.lastValue.monitorConfig)
    return checkInCaptor.firstValue.monitorConfig
  }

  @Configuration
  @EnableAspectJAutoProxy(proxyTargetClass = true)
  @Import(SentryCheckInAdviceConfiguration::class, SentryCheckInPointcutConfiguration::class)
  open class Config {

    @Bean open fun sampleService() = SampleService()

    @Bean open fun sampleServiceNoSlug() = SampleServiceNoSlug()

    @Bean open fun sampleServiceHeartbeat() = SampleServiceHeartbeat()

    @Bean open fun sampleServiceSpringProperties() = SampleServiceSpringProperties()

    @Bean open fun sampleServiceScheduled() = SampleServiceScheduled()

    @Bean
    open fun scopes(): IScopes {
      val scopes = mock<IScopes>()
      Sentry.setCurrentScopes(scopes)
      return scopes
    }

    companion object {
      @Bean
      @JvmStatic
      fun propertySourcesPlaceholderConfigurer() = MyPropertyPlaceholderConfigurer()
    }
  }

  open class SampleService {

    @SentryCheckIn("monitor_slug_1") open fun hello() = 1

    @SentryCheckIn("monitor_slug_1e")
    open fun oops() {
      throw RuntimeException("thrown on purpose")
    }
  }

  open class SampleServiceNoSlug {

    @SentryCheckIn open fun hello() = 1
  }

  open class SampleServiceHeartbeat {

    @SentryCheckIn(monitorSlug = "monitor_slug_2", heartbeat = true) open fun hello() = 1

    @SentryCheckIn(monitorSlug = "monitor_slug_2e", heartbeat = true)
    open fun oops() {
      throw RuntimeException("thrown on purpose")
    }
  }

  open class SampleServiceSpringProperties {

    @SentryCheckIn("\${my.cron.slug}", heartbeat = true) open fun hello() = 1

    @SentryCheckIn("\${my.cron.unresolved.property}", heartbeat = true)
    open fun helloUnresolvedProperty() = 1

    @SentryCheckIn("\${my.cron.exception.property}", heartbeat = true)
    open fun helloExceptionProperty() = 1
  }

  open class SampleServiceScheduled {

    @SentryCheckIn("cron") @Scheduled(cron = "0 15 10 * * MON-FRI") open fun cron() {}

    @SentryCheckIn("cron_properties")
    @Scheduled(cron = "\${my.cron.schedule}", zone = "\${my.cron.zone}")
    open fun cronFromProperties() {}

    @SentryCheckIn("fixed_rate") @Scheduled(fixedRate = 300_000) open fun fixedRateMinutes() {}

    @SentryCheckIn("fixed_delay_hours")
    @Scheduled(fixedDelay = 2, timeUnit = TimeUnit.HOURS)
    open fun fixedDelayHours() {}

    @SentryCheckIn("fixed_delay_properties")
    @Scheduled(fixedDelayString = "\${my.cron.delay}")
    open fun fixedDelayFromProperties() {}

    @SentryCheckIn("fixed_rate_simple")
    @Scheduled(fixedRateString = "5m")
    open fun fixedRateSimpleStyle() {}

    @SentryCheckIn("fixed_rate_unparseable")
    @Scheduled(fixedRateString = "5 minutes")
    open fun fixedRateUnparseable() {}

    @SentryCheckIn("multiple")
    @Scheduled(cron = "0 0 1 * * *")
    @Scheduled(cron = "0 0 13 * * *")
    open fun multipleSchedules() {}

    @SentryCheckIn("opt_out", upsertMonitorConfig = false)
    @Scheduled(cron = "0 0 1 * * *")
    open fun optOut() {}

    @SentryCheckIn("heartbeat", heartbeat = true)
    @Scheduled(cron = "0 0 1 * * *")
    open fun heartbeat() {}
  }

  class MyPropertyPlaceholderConfigurer : PropertySourcesPlaceholderConfigurer() {

    override fun doProcessProperties(
      beanFactoryToProcess: ConfigurableListableBeanFactory,
      valueResolver: StringValueResolver,
    ) {
      val wrappedResolver = StringValueResolver { strVal: String ->
        if ("\${my.cron.exception.property}".equals(strVal)) {
          throw IllegalArgumentException("Cannot resolve property: $strVal")
        } else {
          valueResolver.resolveStringValue(strVal)
        }
      }
      super.doProcessProperties(beanFactoryToProcess, wrappedResolver)
    }
  }
}
