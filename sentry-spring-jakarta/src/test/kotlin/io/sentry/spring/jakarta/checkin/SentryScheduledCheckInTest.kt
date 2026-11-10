package io.sentry.spring.jakarta.checkin

import io.sentry.CheckIn
import io.sentry.CheckInStatus
import io.sentry.ILogger
import io.sentry.IScopes
import io.sentry.ISentryLifecycleToken
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.protocol.SentryId
import java.lang.reflect.Modifier
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.aop.Advisor
import org.springframework.aop.Pointcut
import org.springframework.aop.framework.ProxyFactory
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
  private val COLLISION_WARNING =
    "Monitor slug \"%s\" is used by both %s and %s, so their check-ins go to one monitor. Add @SentryCheckIn(\"your-slug\") to one of them."

  @Autowired lateinit var jobs: Jobs

  @Autowired lateinit var excludedJobs: ExcludedJobs

  @Autowired lateinit var finalJobs: FinalJobs

  @Autowired lateinit var overridingJobs: OverridingJobs

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
  fun `tasks with unsupported schedules are not monitored`() {
    jobs.fixedDelay()
    verify(scopes, never()).captureCheckIn(any())
  }

  @Test
  fun `@SentryCheckIn with an unsupported schedule checks in without config`() {
    val checkIns = capture { jobs.explicitFixedDelay() }
    assertEquals(listOf("explicit-delay", "explicit-delay"), checkIns.map { it.monitorSlug })
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
    assertNull(checkIns[0].monitorConfig)
  }

  @Test
  fun `methods without @Scheduled are not monitored`() {
    jobs.notScheduled()
    verify(scopes, never()).captureCheckIn(any())
  }

  @Test
  fun `overriding methods without @Scheduled are not monitored`() {
    overridingJobs.run()
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
  fun `private and static methods are not matched`() {
    val pointcut = SentryScheduledCheckInPointcut(null)
    val privateMethod = HiddenJobs::class.java.getDeclaredMethod("hidden")
    val staticMethod = HiddenJobs::class.java.getDeclaredMethod("static")
    assertTrue(Modifier.isPrivate(privateMethod.modifiers))
    assertTrue(Modifier.isStatic(staticMethod.modifiers))
    assertFalse(pointcut.matches(privateMethod, HiddenJobs::class.java))
    assertFalse(pointcut.matches(staticMethod, HiddenJobs::class.java))
    assertTrue(pointcut.matches(Jobs::class.java.getMethod("syncUsers"), Jobs::class.java))
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

  @Test
  fun `warns once when two methods derive the same slug`() {
    val logger = mockLogger()
    val advice = SentryCheckInAdvice(scopes, true)
    val cleanupJob = proxy(CleanupJob(), advice)
    val cleanup = proxy(Cleanup(), advice)

    cleanupJob.run()
    cleanup.jobRun()
    cleanup.jobRun()
    cleanupJob.run()

    verify(logger, times(1))
      .log(
        eq(SentryLevel.WARNING),
        eq(COLLISION_WARNING),
        eq("sentry-scheduled-check-in-test-cleanup-job-run"),
        eq("${CleanupJob::class.java.name}.run"),
        eq("${Cleanup::class.java.name}.jobRun"),
      )
  }

  @Test
  fun `does not warn when derived slugs differ or the same method runs again`() {
    val logger = mockLogger()
    val advice = SentryCheckInAdvice(scopes, true)
    val cleanupJob = proxy(CleanupJob(), advice)
    val otherJobs = proxy(Jobs(), advice)

    cleanupJob.run()
    cleanupJob.run()
    otherJobs.syncUsers()

    verify(logger, never()).log(eq(SentryLevel.WARNING), eq(COLLISION_WARNING), any(), any(), any())
  }

  private fun mockLogger(): ILogger {
    val logger = mock<ILogger>()
    val options =
      SentryOptions().apply {
        isDebug = true
        setLogger(logger)
      }
    whenever(scopes.options).thenReturn(options)
    return logger
  }

  private fun <T : Any> proxy(target: T, advice: SentryCheckInAdvice): T {
    val factory = ProxyFactory(target)
    factory.isProxyTargetClass = true
    factory.addAdvice(advice)
    @Suppress("UNCHECKED_CAST")
    return factory.proxy as T
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

    @Bean open fun overridingJobs() = OverridingJobs()

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

    @SentryCheckIn("explicit-delay") @Scheduled(fixedDelay = 1000) open fun explicitFixedDelay() {}

    @Scheduled(cron = "0 0 2 * * *") open fun excluded() {}

    open fun notScheduled() {}

    @Scheduled(cron = "0 0 2 * * *") open fun reactive(): Mono<Void> = Mono.empty()
  }

  open class CleanupJob {
    @Scheduled(cron = "0 0 2 * * *") open fun run() {}
  }

  open class Cleanup {
    @Scheduled(cron = "0 0 2 * * *") open fun jobRun() {}
  }

  open class HiddenJobs {
    @Scheduled(cron = "0 0 2 * * *") private fun hidden() {}

    companion object {
      @JvmStatic @Scheduled(cron = "0 0 2 * * *") fun static() {}
    }
  }

  open class ExcludedJobs {
    @Scheduled(cron = "0 0 2 * * *") open fun run() {}
  }

  class FinalJobs {
    @Scheduled(cron = "0 0 2 * * *") fun run() {}
  }

  open class ParentJobs {
    @Scheduled(cron = "0 0 2 * * *") open fun run() {}
  }

  open class OverridingJobs : ParentJobs() {
    override fun run() {}
  }
}
