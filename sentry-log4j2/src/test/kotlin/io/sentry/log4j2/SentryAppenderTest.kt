package io.sentry.log4j2

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.sentry.HubAdapter
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
    private class Fixture() {
        val loggerContext = LogManager.getContext() as LoggerContext
        lateinit var transport: ITransport

        fun getSut(transport: ITransport = mock(), minimumBreadcrumbLevel: Level? = null, minimumEventLevel: Level? = null): ExtendedLogger {
            this.transport = transport
            loggerContext.start()
            val config: Configuration = loggerContext.configuration
            val appender = SentryAppender("sentry", null, "http://key@localhost/proj", minimumBreadcrumbLevel, minimumEventLevel, transport, HubAdapter.getInstance())
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
        val transport = mock<ITransport>()
        Sentry.init {
            it.dsn = "http://key@localhost/proj"
            it.environment = "manual-environment"
            it.setTransport(transport)
        }
        val logger = fixture.getSut(transport = transport)
        logger.error("testing environment field")

        await.untilAsserted {
            verify(fixture.transport).send(checkEvent { event ->
                assertEquals("manual-environment", event.environment)
            })
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
            })
        }
    }

    @Test
    fun `event date is in UTC`() {
        val logger = fixture.getSut(minimumEventLevel = Level.DEBUG)
        val utcTime = LocalDateTime.now(ZoneId.of("UTC"))

        logger.debug("testing event date")

        await.untilAsserted {
            verify(fixture.transport).send(checkEvent { event ->
                val eventTime = Instant.ofEpochMilli(event.timestamp.time)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime()

                assertTrue { eventTime.plusSeconds(1).isAfter(utcTime) }
                assertTrue { eventTime.minusSeconds(1).isBefore(utcTime) }
            })
        }
    }

    @Test
    fun `converts trace log level to Sentry level`() {
        val logger = fixture.getSut(minimumEventLevel = Level.TRACE)
        logger.trace("testing trace level")

        await.untilAsserted {
            verify(fixture.transport).send(checkEvent { event ->
                assertEquals(SentryLevel.DEBUG, event.level)
            })
        }
    }

    @Test
    fun `converts debug log level to Sentry level`() {
        val logger = fixture.getSut(minimumEventLevel = Level.DEBUG)
        logger.debug("testing debug level")

        await.untilAsserted {
            verify(fixture.transport).send(checkEvent { event ->
                assertEquals(SentryLevel.DEBUG, event.level)
            })
        }
    }

    @Test
    fun `converts info log level to Sentry level`() {
        val logger = fixture.getSut(minimumEventLevel = Level.INFO)
        logger.info("testing info level")

        await.untilAsserted {
            verify(fixture.transport).send(checkEvent { event ->
                assertEquals(SentryLevel.INFO, event.level)
            })
        }
    }

    @Test
    fun `converts warn log level to Sentry level`() {
        val logger = fixture.getSut(minimumEventLevel = Level.WARN)
        logger.warn("testing warn level")

        await.untilAsserted {
            verify(fixture.transport).send(checkEvent { event ->
                assertEquals(SentryLevel.WARNING, event.level)
            })
        }
    }

    @Test
    fun `converts error log level to Sentry level`() {
        val logger = fixture.getSut(minimumEventLevel = Level.ERROR)
        logger.error("testing error level")

        await.untilAsserted {
            verify(fixture.transport).send(checkEvent { event ->
                assertEquals(SentryLevel.ERROR, event.level)
            })
        }
    }

    @Test
    fun `converts fatal log level to Sentry level`() {
        val logger = fixture.getSut(minimumEventLevel = Level.FATAL)
        logger.fatal("testing fatal level")

        await.untilAsserted {
            verify(fixture.transport).send(checkEvent { event ->
                assertEquals(SentryLevel.FATAL, event.level)
            })
        }
    }

    @Test
    fun `attaches thread information`() {
        val logger = fixture.getSut(minimumEventLevel = Level.WARN)
        logger.warn("testing thread information")

        await.untilAsserted {
            verify(fixture.transport).send(checkEvent { event ->
                assertNotNull(event.getExtra("thread_name"))
            })
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
            })
        }
    }

    @Test
    fun `does not create MDC context when no MDC tags are set`() {
        val logger = fixture.getSut(minimumEventLevel = Level.WARN)
        logger.warn("testing without MDC tags")

        await.untilAsserted {
            verify(fixture.transport).send(checkEvent { event ->
                assertFalse(event.contexts.containsKey("MDC"))
            })
        }
    }

    @Test
    fun `sets SDK version`() {
        val logger = fixture.getSut(minimumEventLevel = Level.INFO)
        logger.info("testing sdk version")

        await.untilAsserted {
            verify(fixture.transport).send(checkEvent { event ->
                assertEquals(BuildConfig.SENTRY_LOG4J2_SDK_NAME, event.sdk.name)
                assertEquals(BuildConfig.VERSION_NAME, event.sdk.version)
                assertNotNull(event.sdk.packages)
                assertTrue(event.sdk.packages!!.any { pkg ->
                    "maven:sentry-log4j2" == pkg.name &&
                        BuildConfig.VERSION_NAME == pkg.version
                })
            })
        }
    }

    @Test
    fun `attaches breadcrumbs with level higher than minimumBreadcrumbLevel`() {
        val logger = fixture.getSut(minimumEventLevel = Level.WARN, minimumBreadcrumbLevel = Level.DEBUG)
        val utcTime = LocalDateTime.now(ZoneId.of("UTC"))

        logger.debug("this should be a breadcrumb #1")
        logger.info("this should be a breadcrumb #2")
        logger.warn("testing message with breadcrumbs")

        await.untilAsserted {
            verify(fixture.transport).send(checkEvent { event ->
                assertEquals(2, event.breadcrumbs.size)
                val breadcrumb = event.breadcrumbs[0]
                val breadcrumbTime = Instant.ofEpochMilli(event.timestamp.time)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime()
                assertTrue { breadcrumbTime.plusSeconds(1).isAfter(utcTime) }
                assertTrue { breadcrumbTime.minusSeconds(1).isBefore(utcTime) }
                assertEquals("this should be a breadcrumb #1", breadcrumb.message)
                assertEquals("io.sentry.log4j2.SentryAppenderTest", breadcrumb.category)
                assertEquals(SentryLevel.DEBUG, breadcrumb.level)
            })
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
            })
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
            })
        }
    }

    @Test
    fun `uses options set in properties file`() {
        val logger = fixture.getSut()
        logger.error("some event")

        await.untilAsserted {
            verify(fixture.transport).send(checkEvent { event ->
                assertEquals("release from sentry.properties", event.release)
            })
        }
    }
}
