package io.sentry.log4j2

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.HubAdapter
import io.sentry.ITransportFactory
import io.sentry.Sentry
import io.sentry.SentryLevel
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
import kotlin.test.assertTrue
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.ThreadContext
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.config.AppenderRef
import org.apache.logging.log4j.core.config.Configuration
import org.apache.logging.log4j.core.config.LoggerConfig
import org.apache.logging.log4j.spi.ExtendedLogger
import org.awaitility.kotlin.await

class SentryAppenderTest {
    private class Fixture {
        val loggerContext = LogManager.getContext() as LoggerContext
        var transportFactory = mock<ITransportFactory>()
        var transport = mock<ITransport>()
        val utcTimeZone: ZoneId = ZoneId.of("UTC")

        init {
            whenever(transportFactory.create(any(), any())).thenReturn(transport)
        }

        fun getSut(transportFactory: ITransportFactory? = null, minimumBreadcrumbLevel: Level? = null, minimumEventLevel: Level? = null): ExtendedLogger {
            if (transportFactory != null) {
                this.transportFactory = transportFactory
            }
            loggerContext.start()
            val config: Configuration = loggerContext.configuration
            val appender = SentryAppender("sentry", null, "http://key@localhost/proj", minimumBreadcrumbLevel, minimumEventLevel, this.transportFactory, HubAdapter.getInstance())
            config.addAppender(appender)

            val ref = AppenderRef.createAppenderRef("sentry", null, null)

            val loggerConfig = LoggerConfig.createLogger(false, Level.TRACE, "sentry_logger", "true", arrayOf(ref), null, config, null)
            loggerConfig.addAppender(appender, null, null)
            config.addLogger(SentryAppenderTest::class.java.name, loggerConfig)

            loggerContext.updateLoggers(config)

            appender.start()
            loggerContext.start()

            return LogManager.getContext().getLogger(SentryAppenderTest::class.java.name)
        }
    }

    private var fixture = Fixture()

    @AfterTest
    fun `stop log4j2`() {
        fixture.loggerContext.stop()
        Sentry.close()
    }

    @BeforeTest
    fun `clear MDC`() {
        ThreadContext.clearAll()
    }

    @Test
    fun `does not initialize Sentry if Sentry is already enabled`() {
        Sentry.init {
            it.dsn = "http://key@localhost/proj"
            it.environment = "manual-environment"
            it.setTransportFactory(fixture.transportFactory)
        }
        val logger = fixture.getSut()
        logger.error("testing environment field")

        await.untilAsserted {
            verify(fixture.transport).send(checkEvent { event ->
                assertEquals("manual-environment", event.environment)
            }, anyOrNull())
        }
    }

    @Test
    fun `converts message`() {
        val logger = fixture.getSut(minimumEventLevel = Level.DEBUG)
        logger.debug("testing message conversion {}, {}", 1, 2)

        await.untilAsserted {
            verify(fixture.transport).send(checkEvent { event ->
                assertEquals("testing message conversion 1, 2", event.message.formatted)
                assertEquals("testing message conversion {}, {}", event.message.message)
                assertEquals(listOf("1", "2"), event.message.params)
                assertEquals("io.sentry.log4j2.SentryAppenderTest", event.logger)
            }, anyOrNull())
        }
    }

    @Test
    fun `event date is in UTC`() {
        val logger = fixture.getSut(minimumEventLevel = Level.DEBUG)
        val utcTime = LocalDateTime.now(fixture.utcTimeZone)

        logger.debug("testing event date")

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
        val logger = fixture.getSut(minimumEventLevel = Level.TRACE)
        logger.trace("testing trace level")

        await.untilAsserted {
            verify(fixture.transport).send(checkEvent { event ->
                assertEquals(SentryLevel.DEBUG, event.level)
            }, anyOrNull())
        }
    }

    @Test
    fun `converts debug log level to Sentry level`() {
        val logger = fixture.getSut(minimumEventLevel = Level.DEBUG)
        logger.debug("testing debug level")

        await.untilAsserted {
            verify(fixture.transport).send(checkEvent { event ->
                assertEquals(SentryLevel.DEBUG, event.level)
            }, anyOrNull())
        }
    }

    @Test
    fun `converts info log level to Sentry level`() {
        val logger = fixture.getSut(minimumEventLevel = Level.INFO)
        logger.info("testing info level")

        await.untilAsserted {
            verify(fixture.transport).send(checkEvent { event ->
                assertEquals(SentryLevel.INFO, event.level)
            }, anyOrNull())
        }
    }

    @Test
    fun `converts warn log level to Sentry level`() {
        val logger = fixture.getSut(minimumEventLevel = Level.WARN)
        logger.warn("testing warn level")

        await.untilAsserted {
            verify(fixture.transport).send(checkEvent { event ->
                assertEquals(SentryLevel.WARNING, event.level)
            }, anyOrNull())
        }
    }

    @Test
    fun `converts error log level to Sentry level`() {
        val logger = fixture.getSut(minimumEventLevel = Level.ERROR)
        logger.error("testing error level")

        await.untilAsserted {
            verify(fixture.transport).send(checkEvent { event ->
                assertEquals(SentryLevel.ERROR, event.level)
            }, anyOrNull())
        }
    }

    @Test
    fun `converts fatal log level to Sentry level`() {
        val logger = fixture.getSut(minimumEventLevel = Level.FATAL)
        logger.fatal("testing fatal level")

        await.untilAsserted {
            verify(fixture.transport).send(checkEvent { event ->
                assertEquals(SentryLevel.FATAL, event.level)
            }, anyOrNull())
        }
    }

    @Test
    fun `attaches thread information`() {
        val logger = fixture.getSut(minimumEventLevel = Level.WARN)
        logger.warn("testing thread information")

        await.untilAsserted {
            verify(fixture.transport).send(checkEvent { event ->
                assertNotNull(event.getExtra("thread_name"))
            }, anyOrNull())
        }
    }

    @Test
    fun `sets tags from ThreadContext`() {
        val logger = fixture.getSut(minimumEventLevel = Level.WARN)
        ThreadContext.put("key", "value")
        logger.warn("testing MDC tags")

        await.untilAsserted {
            verify(fixture.transport).send(checkEvent { event ->
                assertEquals(mapOf("key" to "value"), event.contexts["Context Data"])
            }, anyOrNull())
        }
    }

    @Test
    fun `does not create MDC context when no MDC tags are set`() {
        val logger = fixture.getSut(minimumEventLevel = Level.WARN)
        logger.warn("testing without MDC tags")

        await.untilAsserted {
            verify(fixture.transport).send(checkEvent { event ->
                assertFalse(event.contexts.containsKey("MDC"))
            }, anyOrNull())
        }
    }

    @Test
    fun `sets SDK version`() {
        val logger = fixture.getSut(minimumEventLevel = Level.INFO)
        logger.info("testing sdk version")

        await.untilAsserted {
            verify(fixture.transport).send(checkEvent { event ->
                assertNotNull(event.sdk) {
                    assertEquals(BuildConfig.SENTRY_LOG4J2_SDK_NAME, it.name)
                    assertEquals(BuildConfig.VERSION_NAME, it.version)
                    assertNotNull(it.packages)
                    assertTrue(it.packages!!.any { pkg ->
                        "maven:sentry-log4j2" == pkg.name &&
                            BuildConfig.VERSION_NAME == pkg.version
                    })
                }
            }, anyOrNull())
        }
    }

    @Test
    fun `attaches breadcrumbs with level higher than minimumBreadcrumbLevel`() {
        val logger = fixture.getSut(minimumEventLevel = Level.WARN, minimumBreadcrumbLevel = Level.DEBUG)
        val utcTime = LocalDateTime.now(fixture.utcTimeZone)

        logger.debug("this should be a breadcrumb #1")
        logger.info("this should be a breadcrumb #2")
        logger.warn("testing message with breadcrumbs")

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
                assertEquals("io.sentry.log4j2.SentryAppenderTest", breadcrumb.category)
                assertEquals(SentryLevel.DEBUG, breadcrumb.level)
            }, anyOrNull())
        }
    }

    @Test
    fun `does not attach breadcrumbs with level lower than minimumBreadcrumbLevel`() {
        val logger = fixture.getSut(minimumEventLevel = Level.WARN, minimumBreadcrumbLevel = Level.INFO)

        logger.debug("this should NOT be a breadcrumb")
        logger.info("this should be a breadcrumb")
        logger.warn("testing message with breadcrumbs")

        await.untilAsserted {
            verify(fixture.transport).send(checkEvent { event ->
                assertEquals(1, event.breadcrumbs.size)
                assertEquals("this should be a breadcrumb", event.breadcrumbs[0].message)
            }, anyOrNull())
        }
    }

    @Test
    fun `attaches breadcrumbs for default appender configuration`() {
        val logger = fixture.getSut()

        logger.debug("this should not be a breadcrumb as the level is lower than the minimum INFO")
        logger.info("this should be a breadcrumb")
        logger.warn("this should not be sent as the event but be a breadcrumb")
        logger.error("this should be sent as the event")

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
        val logger = fixture.getSut()
        logger.error("some event")

        await.untilAsserted {
            verify(fixture.transport).send(checkEvent { event ->
                assertEquals("release from sentry.properties", event.release)
            }, anyOrNull())
        }
    }
}
