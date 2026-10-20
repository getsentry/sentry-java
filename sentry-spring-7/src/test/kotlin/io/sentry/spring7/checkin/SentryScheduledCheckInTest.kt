package io.sentry.spring7.checkin

import io.sentry.CheckIn
import io.sentry.CheckInStatus
import io.sentry.IScopes
import io.sentry.ISentryLifecycleToken
import io.sentry.SentryOptions
import io.sentry.protocol.SentryId
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.assertThrows
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.aop.Advisor
import org.springframework.aop.Pointcut
import org.springframework.aop.support.AopUtils
import org.springframework.aop.support.DefaultPointcutAdvisor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.context.annotation.Import
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.test.context.junit4.SpringRunner
import reactor.core.publisher.Mono

@RunWith(SpringRunner::class)
@SpringJUnitConfig(SentryScheduledCheckInTest.Config::class)
@TestPropertySource(properties = ["my.cron.schedule = 0 30 2 * * *"])
class SentryScheduledCheckInTest {
  private val SLUG = "sentry-scheduled-check-in-test-jobs-sync-users"

  @Autowired lateinit var jobs: Jobs

  @Autowired lateinit var excludedJobs: ExcludedJobs

  @Autowired lateinit var finalJobs: FinalJobs

  @Autowired lateinit var scopes: IScopes

  @BeforeTest
  fun setup() {
    reset(scopes)
    whenever(scopes.options).thenReturn(SentryOptions())
    whenever(scopes.forkedScopes(any())).thenReturn(scopes)
    whenever(scopes.makeCurrent()).thenReturn(mock<ISentryLifecycleToken>())
    whenever(scopes.captureCheckIn(any())).thenReturn(SentryId())
  }

  @Test
  fun `scheduled method without @SentryCheckIn is monitored with a derived slug and config`() {
    val checkIns = capture { jobs.syncUsers() }
    assertEquals(2, checkIns.size)
    assertEquals(SLUG, checkIns[0].monitorSlug)
    assertEquals(CheckInStatus.IN_PROGRESS.apiName(), checkIns[0].status)
    val config = assertNotNull(checkIns[0].monitorConfig)
    assertEquals("0 2 * * *", config.schedule.value)
    assertEquals("Europe/Vienna", config.timezone)
    assertEquals(SLUG, checkIns[1].monitorSlug)
    assertEquals(CheckInStatus.OK.apiName(), checkIns[1].status)
  }

  @Test
  fun `cron placeholder is resolved`() {
    val checkIns = capture { jobs.fromProperty() }
    assertEquals("30 2 * * *", checkIns[0].monitorConfig!!.schedule.value)
  }

  @Test
  fun `fixed delay is monitored without config`() {
    val checkIns = capture { jobs.fixedDelay() }
    assertEquals(2, checkIns.size)
    assertNull(checkIns[0].monitorConfig)
  }

  @Test
  fun `failing job sends an error check-in and rethrows`() {
    val checkIns = capture { assertThrows<IllegalStateException> { jobs.oops() } }
    assertEquals(CheckInStatus.ERROR.apiName(), checkIns[1].status)
  }

  @Test
  fun `@SentryCheckIn keeps its slug and is only checked in once`() {
    val checkIns = capture { jobs.explicit() }
    assertEquals(listOf("explicit", "explicit"), checkIns.map { it.monitorSlug })
  }

  @Test
  fun `methods without @Scheduled are not monitored`() {
    jobs.notScheduled()
    verify(scopes, never()).captureCheckIn(any())
  }

  @Test
  fun `reactive methods are not monitored`() {
    jobs.reactive().block()
    verify(scopes, never()).captureCheckIn(any())
  }

  @Test
  fun `excluded tasks are not monitored`() {
    jobs.excluded()
    excludedJobs.run()
    verify(scopes, never()).captureCheckIn(any())
    assertFalse(AopUtils.isAopProxy(excludedJobs))
  }

  @Test
  fun `final classes are not proxied`() {
    finalJobs.run()
    verify(scopes, never()).captureCheckIn(any())
    assertFalse(AopUtils.isAopProxy(finalJobs))
    assertTrue(AopUtils.isAopProxy(jobs))
  }

  @Test
  fun `slugs are kebab case and at most 50 characters`() {
    assertEquals(
      "report-jobs-send-daily-report",
      SentryCheckInAdvice.scheduledTaskSlug("ReportJobs.sendDailyReport"),
    )
    assertEquals(
      "http-client-jobs-run",
      SentryCheckInAdvice.scheduledTaskSlug("HTTPClientJobs.run"),
    )
    assertEquals("outer-inner-run2-fa", SentryCheckInAdvice.scheduledTaskSlug("Outer.Inner.run2FA"))
    val first =
      SentryCheckInAdvice.scheduledTaskSlug(
        "AVeryLongScheduledJobClassNameForReporting.firstMethod"
      )
    val second =
      SentryCheckInAdvice.scheduledTaskSlug(
        "AVeryLongScheduledJobClassNameForReporting.secondMethod"
      )
    assertTrue(first.length <= 50)
    assertTrue(first.startsWith("a-very-long-scheduled-job-class-name-for-"))
    assertTrue(first != second)
    assertEquals(
      first,
      SentryCheckInAdvice.scheduledTaskSlug(
        "AVeryLongScheduledJobClassNameForReporting.firstMethod"
      ),
    )
  }

  @Test
  fun `task name uses the user class`() {
    assertEquals(
      "SentryScheduledCheckInTest.Jobs.syncUsers",
      SentryCheckInAdvice.scheduledTaskName(
        jobs.javaClass,
        Jobs::class.java.getMethod("syncUsers"),
      ),
    )
  }

  private fun capture(block: () -> Unit): List<CheckIn> {
    val captor = argumentCaptor<CheckIn>()
    block()
    verify(scopes, atLeast(0)).captureCheckIn(captor.capture())
    return captor.allValues
  }

  @Configuration
  @EnableAspectJAutoProxy(proxyTargetClass = true)
  @Import(SentryCheckInPointcutConfiguration::class)
  open class Config {

    @Bean open fun jobs() = Jobs()

    @Bean open fun excludedJobs() = ExcludedJobs()

    @Bean open fun finalJobs() = FinalJobs()

    @Bean open fun scopes(): IScopes = mock()

    @Bean
    open fun sentryCheckInAdvisor(
      @Qualifier("sentryCheckInPointcut") pointcut: Pointcut,
      scopes: IScopes,
    ): Advisor = DefaultPointcutAdvisor(pointcut, SentryCheckInAdvice(scopes))

    @Bean open fun sentryScheduledCheckInAdvice(scopes: IScopes) = SentryCheckInAdvice(scopes, true)

    @Bean
    open fun sentryScheduledCheckInAdvisor(
      @Qualifier("sentryScheduledCheckInAdvice") advice: SentryCheckInAdvice
    ): Advisor =
      DefaultPointcutAdvisor(
        SentryScheduledCheckInPointcut(
          listOf("SentryScheduledCheckInTest.Jobs.excluded", ".*\\.ExcludedJobs\\..*", " ")
        ),
        advice,
      )

    companion object {
      @Bean
      @JvmStatic
      fun propertySourcesPlaceholderConfigurer() = PropertySourcesPlaceholderConfigurer()
    }
  }

  open class Jobs {
    @Scheduled(cron = "0 0 2 * * *", zone = "Europe/Vienna") open fun syncUsers() {}

    @Scheduled(cron = "\${my.cron.schedule}") open fun fromProperty() {}

    @Scheduled(fixedDelay = 1000) open fun fixedDelay() {}

    @Scheduled(cron = "0 0 2 * * *")
    open fun oops() {
      throw IllegalStateException("thrown on purpose")
    }

    @SentryCheckIn("explicit") @Scheduled(cron = "0 0 2 * * *") open fun explicit() {}

    @Scheduled(cron = "0 0 2 * * *") open fun excluded() {}

    open fun notScheduled() {}

    @Scheduled(cron = "0 0 2 * * *") open fun reactive(): Mono<Void> = Mono.empty()
  }

  open class ExcludedJobs {
    @Scheduled(cron = "0 0 2 * * *") open fun run() {}
  }

  class FinalJobs {
    @Scheduled(cron = "0 0 2 * * *") fun run() {}
  }
}
