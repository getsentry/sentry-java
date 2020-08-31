package io.sentry.logback

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.core.SentryEvent
import io.sentry.core.SentryLevel
import io.sentry.core.SentryOptions
import io.sentry.core.transport.ITransport
import io.sentry.core.transport.TransportResult
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.awaitility.kotlin.await
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC

class SentryAppenderTest {
    private class Fixture(minimumBreadcrumbLevel: Level? = null, minimumEventLevel: Level? = null) {
        val transport = mock<ITransport>()
        val logger: Logger = LoggerFactory.getLogger(SentryAppenderTest::class.java)
        val loggerContext = LoggerFactory.getILoggerFactory() as LoggerContext

        init {
            whenever(transport.send(any<SentryEvent>())).thenReturn(TransportResult.success())
            val appender = SentryAppender()
            val options = SentryOptions()
            options.dsn = "http://key@localhost/proj"
            appender.setOptions(options)
            appender.setMinimumBreadcrumbLevel(minimumBreadcrumbLevel)
            appender.setMinimumEventLevel(minimumEventLevel)
            appender.context = loggerContext
            appender.setTransport(transport)
            val rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME)
            rootLogger.level = Level.TRACE
            rootLogger.addAppender(appender)
            appender.start()
            loggerContext.start()
        }
    }

    private lateinit var fixture: Fixture

    @AfterTest
    fun `stop logback`() {
        fixture.loggerContext.stop()
    }

    @BeforeTest
    fun `clear MDC`() {
        MDC.clear()
    }

    @Test
    fun `converts message`() {
        fixture = Fixture(minimumEventLevel = Level.DEBUG)
        fixture.logger.debug("testing message conversion {}, {}", 1, 2)

        await.untilAsserted {
            verify(fixture.transport).send(check { it: SentryEvent ->
                assertEquals("testing message conversion 1, 2", it.message.formatted)
                assertEquals("testing message conversion {}, {}", it.message.message)
                assertEquals(listOf("1", "2"), it.message.params)
                assertEquals("io.sentry.logback.SentryAppenderTest", it.logger)
            })
        }
    }

    @Test
    fun `event date is in UTC`() {
        fixture = Fixture(minimumEventLevel = Level.DEBUG)
        val utcTime = LocalDateTime.now(ZoneId.of("UTC"))

        fixture.logger.debug("testing event date")

        await.untilAsserted {
            verify(fixture.transport).send(check { it: SentryEvent ->
                val eventTime = Instant.ofEpochMilli(it.timestamp.time)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime()

                assertTrue { eventTime.plusSeconds(1).isAfter(utcTime) }
                assertTrue { eventTime.minusSeconds(1).isBefore(utcTime) }
            })
        }
    }

    @Test
    fun `converts trace log level to Sentry level`() {
        fixture = Fixture(minimumEventLevel = Level.TRACE)
        fixture.logger.trace("testing trace level")

        await.untilAsserted {
            verify(fixture.transport).send(check { it: SentryEvent ->
                assertEquals(SentryLevel.DEBUG, it.level)
            })
        }
    }

    @Test
    fun `converts debug log level to Sentry level`() {
        fixture = Fixture(minimumEventLevel = Level.DEBUG)
        fixture.logger.debug("testing debug level")

        await.untilAsserted {
            verify(fixture.transport).send(check { it: SentryEvent ->
                assertEquals(SentryLevel.DEBUG, it.level)
            })
        }
    }

    @Test
    fun `converts info log level to Sentry level`() {
        fixture = Fixture(minimumEventLevel = Level.INFO)
        fixture.logger.info("testing info level")

        await.untilAsserted {
            verify(fixture.transport).send(check { it: SentryEvent ->
                assertEquals(SentryLevel.INFO, it.level)
            })
        }
    }

    @Test
    fun `converts warn log level to Sentry level`() {
        fixture = Fixture(minimumEventLevel = Level.WARN)
        fixture.logger.warn("testing warn level")

        await.untilAsserted {
            verify(fixture.transport).send(check { it: SentryEvent ->
                assertEquals(SentryLevel.WARNING, it.level)
            })
        }
    }

    @Test
    fun `converts error log level to Sentry level`() {
        fixture = Fixture(minimumEventLevel = Level.ERROR)
        fixture.logger.error("testing error level")

        await.untilAsserted {
            verify(fixture.transport).send(check { it: SentryEvent ->
                assertEquals(SentryLevel.ERROR, it.level)
            })
        }
    }

    @Test
    fun `attaches thread information`() {
        fixture = Fixture(minimumEventLevel = Level.WARN)
        fixture.logger.warn("testing thread information")

        await.untilAsserted {
            verify(fixture.transport).send(check { it: SentryEvent ->
                assertNotNull(it.getExtra("thread_name"))
            })
        }
    }

    @Test
    fun `sets tags from MDC`() {
        fixture = Fixture(minimumEventLevel = Level.WARN)
        MDC.put("key", "value")
        fixture.logger.warn("testing MDC tags")

        await.untilAsserted {
            verify(fixture.transport).send(check { it: SentryEvent ->
                assertEquals(mapOf("key" to "value"), it.contexts["MDC"])
            })
        }
    }

    @Test
    fun `does not create MDC context when no MDC tags are set`() {
        fixture = Fixture(minimumEventLevel = Level.WARN)
        fixture.logger.warn("testing without MDC tags")

        await.untilAsserted {
            verify(fixture.transport).send(check { it: SentryEvent ->
                assertFalse(it.contexts.containsKey("MDC"))
            })
        }
    }

    @Test
    fun `attaches throwable`() {
        fixture = Fixture(minimumEventLevel = Level.WARN)
        val throwable = RuntimeException("something went wrong")
        fixture.logger.warn("testing throwable", throwable)

        await.untilAsserted {
            verify(fixture.transport).send(check { it: SentryEvent ->
                assertEquals(throwable, it.throwable)
            })
        }
    }

    @Test
    fun `sets SDK version`() {
        fixture = Fixture(minimumEventLevel = Level.INFO)
        fixture.logger.info("testing sdk version")

        await.untilAsserted {
            verify(fixture.transport).send(check { it: SentryEvent ->
                assertEquals(BuildConfig.SENTRY_LOGBACK_SDK_NAME, it.sdk.name)
                assertEquals(BuildConfig.VERSION_NAME, it.sdk.version)
                assertNotNull(it.sdk.packages)
                assertTrue(it.sdk.packages!!.any { pkg ->
                    "maven:sentry-logback" == pkg.name &&
                        BuildConfig.VERSION_NAME == pkg.version
                })
            })
        }
    }

    @Test
    fun `attaches breadcrumbs with level higher than minimumBreadcrumbLevel`() {
        fixture = Fixture(minimumBreadcrumbLevel = Level.DEBUG, minimumEventLevel = Level.WARN)
        val utcTime = LocalDateTime.now(ZoneId.of("UTC"))

        fixture.logger.debug("this should be a breadcrumb #1")
        fixture.logger.info("this should be a breadcrumb #2")
        fixture.logger.warn("testing message with breadcrumbs")

        await.untilAsserted {
            verify(fixture.transport).send(check { it: SentryEvent ->
                assertEquals(2, it.breadcrumbs.size)
                val breadcrumb = it.breadcrumbs[0]
                val breadcrumbTime = Instant.ofEpochMilli(it.timestamp.time)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime()
                assertTrue { breadcrumbTime.plusSeconds(1).isAfter(utcTime) }
                assertTrue { breadcrumbTime.minusSeconds(1).isBefore(utcTime) }
                assertEquals("this should be a breadcrumb #1", breadcrumb.message)
                assertEquals("io.sentry.logback.SentryAppenderTest", breadcrumb.category)
                assertEquals(SentryLevel.DEBUG, breadcrumb.level)
            })
        }
    }

    @Test
    fun `does not attach breadcrumbs with level lower than minimumBreadcrumbLevel`() {
        fixture = Fixture(minimumBreadcrumbLevel = Level.INFO, minimumEventLevel = Level.WARN)

        fixture.logger.debug("this should NOT be a breadcrumb")
        fixture.logger.info("this should be a breadcrumb")
        fixture.logger.warn("testing message with breadcrumbs")

        await.untilAsserted {
            verify(fixture.transport).send(check { it: SentryEvent ->
                assertEquals(1, it.breadcrumbs.size)
                assertEquals("this should be a breadcrumb", it.breadcrumbs[0].message)
            })
        }
    }

    @Test
    fun `attaches breadcrumbs for default appender configuration`() {
        fixture = Fixture()

        fixture.logger.debug("this should not be a breadcrumb as the level is lower than the minimum INFO")
        fixture.logger.info("this should be a breadcrumb")
        fixture.logger.warn("this should not be sent as the event but be a breadcrumb")
        fixture.logger.error("this should be sent as the event")

        await.untilAsserted {
            verify(fixture.transport).send(check { it: SentryEvent ->
                assertEquals(2, it.breadcrumbs.size)
                assertEquals("this should be a breadcrumb", it.breadcrumbs[0].message)
                assertEquals("this should not be sent as the event but be a breadcrumb", it.breadcrumbs[1].message)
            })
        }
    }
}
