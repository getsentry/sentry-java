package io.sentry.logback

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.core.status.Status
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.ITransportFactory
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.test.checkEvent
import io.sentry.transport.ITransport
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.awaitility.kotlin.await
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC

class SentryAppenderTest {
    private class Fixture(dsn: String? = "http://key@localhost/proj", minimumBreadcrumbLevel: Level? = null, minimumEventLevel: Level? = null) {
        val logger: Logger = LoggerFactory.getLogger(SentryAppenderTest::class.java)
        val loggerContext = LoggerFactory.getILoggerFactory() as LoggerContext
        val transportFactory = mock<ITransportFactory>()
        val transport = mock<ITransport>()
        val utcTimeZone: ZoneId = ZoneId.of("UTC")

        init {
            whenever(this.transportFactory.create(any(), any())).thenReturn(transport)
            val appender = SentryAppender()
            val options = SentryOptions()
            options.dsn = dsn
            appender.setOptions(options)
            appender.setMinimumBreadcrumbLevel(minimumBreadcrumbLevel)
            appender.setMinimumEventLevel(minimumEventLevel)
            appender.context = loggerContext
            appender.setTransportFactory(transportFactory)
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
        Sentry.close()
    }

    @BeforeTest
    fun `clear MDC`() {
        MDC.clear()
    }

    @Test
    fun `does not initialize Sentry if Sentry is already enabled`() {
        fixture = Fixture()
        Sentry.init {
            it.dsn = "http://key@localhost/proj"
            it.environment = "manual-environment"
            it.setTransportFactory(fixture.transportFactory)
        }
        fixture.logger.error("testing environment field")

        await.untilAsserted {
            verify(fixture.transport).send(checkEvent { event ->
                assertEquals("manual-environment", event.environment)
            }, anyOrNull())
        }
    }

    @Test
    fun `converts message`() {
        fixture = Fixture(minimumEventLevel = Level.DEBUG)
        fixture.logger.debug("testing message conversion {}, {}", 1, 2)

        await.untilAsserted {
            verify(fixture.transport).send(checkEvent { event ->
                assertEquals("testing message conversion 1, 2", event.message.formatted)
                assertEquals("testing message conversion {}, {}", event.message.message)
                assertEquals(listOf("1", "2"), event.message.params)
                assertEquals("io.sentry.logback.SentryAppenderTest", event.logger)
            }, anyOrNull())
        }
    }

    @Test
    fun `event date is in UTC`() {
        fixture = Fixture(minimumEventLevel = Level.DEBUG)
        val utcTime = LocalDateTime.now(fixture.utcTimeZone)

        fixture.logger.debug("testing event date")

        await.untilAsserted {
            verify(fixture.transport).send(checkEvent { event ->
                val eventTime = Instant.ofEpochMilli(event.timestamp.time)
                    .atZone(fixture.utcTimeZone)
                    .toLocalDateTime()

                assertTrue { eventTime.plusSeconds(1).isAfter(utcTime) }
                assertTrue { eventTime.minusSeconds(1).isBefore(utcTime) }
            }, anyOrNull())
        }
    }

    @Test
    fun `converts trace log level to Sentry level`() {
        fixture = Fixture(minimumEventLevel = Level.TRACE)
        fixture.logger.trace("testing trace level")

        await.untilAsserted {
            verify(fixture.transport).send(checkEvent { event ->
                assertEquals(SentryLevel.DEBUG, event.level)
            }, anyOrNull())
        }
    }

    @Test
    fun `converts debug log level to Sentry level`() {
        fixture = Fixture(minimumEventLevel = Level.DEBUG)
        fixture.logger.debug("testing debug level")

        await.untilAsserted {
            verify(fixture.transport).send(checkEvent { event ->
                assertEquals(SentryLevel.DEBUG, event.level)
            }, anyOrNull())
        }
    }

    @Test
    fun `converts info log level to Sentry level`() {
        fixture = Fixture(minimumEventLevel = Level.INFO)
        fixture.logger.info("testing info level")

        await.untilAsserted {
            verify(fixture.transport).send(checkEvent { event ->
                assertEquals(SentryLevel.INFO, event.level)
            }, anyOrNull())
        }
    }

    @Test
    fun `converts warn log level to Sentry level`() {
        fixture = Fixture(minimumEventLevel = Level.WARN)
        fixture.logger.warn("testing warn level")

        await.untilAsserted {
            verify(fixture.transport).send(checkEvent { event ->
                assertEquals(SentryLevel.WARNING, event.level)
            }, anyOrNull())
        }
    }

    @Test
    fun `converts error log level to Sentry level`() {
        fixture = Fixture(minimumEventLevel = Level.ERROR)
        fixture.logger.error("testing error level")

        await.untilAsserted {
            verify(fixture.transport).send(checkEvent { event ->
                assertEquals(SentryLevel.ERROR, event.level)
            }, anyOrNull())
        }
    }

    @Test
    fun `attaches thread information`() {
        fixture = Fixture(minimumEventLevel = Level.WARN)
        fixture.logger.warn("testing thread information")

        await.untilAsserted {
            verify(fixture.transport).send(checkEvent { event ->
                assertNotNull(event.getExtra("thread_name"))
            }, anyOrNull())
        }
    }

    @Test
    fun `sets tags from MDC`() {
        fixture = Fixture(minimumEventLevel = Level.WARN)
        MDC.put("key", "value")
        fixture.logger.warn("testing MDC tags")

        await.untilAsserted {
            verify(fixture.transport).send(checkEvent { event ->
                assertEquals(mapOf("key" to "value"), event.contexts["MDC"])
            }, anyOrNull())
        }
    }

    @Test
    fun `ignore set tags with null values from MDC`() {
        fixture = Fixture(minimumEventLevel = Level.WARN)
        MDC.put("key1", null)
        MDC.put("key2", null)
        fixture.logger.warn("testing MDC tags")

        await.untilAsserted {
            verify(fixture.transport).send(checkEvent { event ->
                assertNull(event.contexts["MDC"])
            }, anyOrNull())
        }
    }

    @Test
    fun `does not create MDC context when no MDC tags are set`() {
        fixture = Fixture(minimumEventLevel = Level.WARN)
        fixture.logger.warn("testing without MDC tags")

        await.untilAsserted {
            verify(fixture.transport).send(checkEvent { event ->
                assertFalse(event.contexts.containsKey("MDC"))
            }, anyOrNull())
        }
    }

    @Test
    fun `sets SDK version`() {
        fixture = Fixture(minimumEventLevel = Level.INFO)
        fixture.logger.info("testing sdk version")

        await.untilAsserted {
            verify(fixture.transport).send(checkEvent { event ->
                assertNotNull(event.sdk) {
                    assertEquals(BuildConfig.SENTRY_LOGBACK_SDK_NAME, it.name)
                    assertEquals(BuildConfig.VERSION_NAME, it.version)
                    assertNotNull(it.packages)
                    assertTrue(it.packages!!.any { pkg ->
                        "maven:io.sentry:sentry-logback" == pkg.name &&
                            BuildConfig.VERSION_NAME == pkg.version
                    })
                }
            }, anyOrNull())
        }
    }

    @Test
    fun `attaches breadcrumbs with level higher than minimumBreadcrumbLevel`() {
        fixture = Fixture(minimumBreadcrumbLevel = Level.DEBUG, minimumEventLevel = Level.WARN)
        val utcTime = LocalDateTime.now(fixture.utcTimeZone)

        fixture.logger.debug("this should be a breadcrumb #1")
        fixture.logger.info("this should be a breadcrumb #2")
        fixture.logger.warn("testing message with breadcrumbs")

        await.untilAsserted {
            verify(fixture.transport).send(checkEvent { event ->
                assertEquals(2, event.breadcrumbs.size)
                val breadcrumb = event.breadcrumbs[0]
                val breadcrumbTime = Instant.ofEpochMilli(event.timestamp.time)
                    .atZone(fixture.utcTimeZone)
                    .toLocalDateTime()
                assertTrue { breadcrumbTime.plusSeconds(1).isAfter(utcTime) }
                assertTrue { breadcrumbTime.minusSeconds(1).isBefore(utcTime) }
                assertEquals("this should be a breadcrumb #1", breadcrumb.message)
                assertEquals("io.sentry.logback.SentryAppenderTest", breadcrumb.category)
                assertEquals(SentryLevel.DEBUG, breadcrumb.level)
            }, anyOrNull())
        }
    }

    @Test
    fun `does not attach breadcrumbs with level lower than minimumBreadcrumbLevel`() {
        fixture = Fixture(minimumBreadcrumbLevel = Level.INFO, minimumEventLevel = Level.WARN)

        fixture.logger.debug("this should NOT be a breadcrumb")
        fixture.logger.info("this should be a breadcrumb")
        fixture.logger.warn("testing message with breadcrumbs")

        await.untilAsserted {
            verify(fixture.transport).send(checkEvent { event ->
                assertEquals(1, event.breadcrumbs.size)
                assertEquals("this should be a breadcrumb", event.breadcrumbs[0].message)
            }, anyOrNull())
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
            verify(fixture.transport).send(checkEvent { event ->
                assertEquals(2, event.breadcrumbs.size)
                assertEquals("this should be a breadcrumb", event.breadcrumbs[0].message)
                assertEquals("this should not be sent as the event but be a breadcrumb", event.breadcrumbs[1].message)
            }, anyOrNull())
        }
    }

    @Test
    fun `uses options set in properties file`() {
        fixture = Fixture()
        fixture.logger.error("some event")

        await.untilAsserted {
            verify(fixture.transport).send(checkEvent { event ->
                assertEquals("release from sentry.properties", event.release)
            }, anyOrNull())
        }
    }

    @Test
    fun `does not initialize Sentry when environment variable with DSN is passed through environment variable that is not set`() {
        // environment variables referenced in the logback.xml that are not set in the OS, have value "ENV_NAME_IS_UNDEFINED"
        fixture = Fixture(dsn = "DSN_IS_UNDEFINED", minimumEventLevel = Level.DEBUG)

        assertTrue(fixture.loggerContext.statusManager.copyOfStatusList.none { it.level == Status.WARN })
    }

    @Test
    fun `does initialize Sentry when DSN is not set`() {
        System.setProperty("sentry.dsn", "http://key@localhost/proj")
        fixture = Fixture(dsn = null, minimumEventLevel = Level.DEBUG)

        assertTrue(Sentry.isEnabled())
        System.clearProperty("sentry.dsn")
    }
}
