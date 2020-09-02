package io.sentry.log4j2

import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.sentry.core.HubAdapter
import io.sentry.core.SentryEvent
import io.sentry.core.SentryLevel
import io.sentry.core.transport.ITransport
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
        val transport = mock<ITransport>()
        val loggerContext = LogManager.getContext() as LoggerContext
        var minimumBreadcrumbLevel: Level? = null
        var minimumEventLevel: Level? = null

        fun getSut(): ExtendedLogger {
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
    }

    @BeforeTest
    fun `clear MDC`() {
        ThreadContext.clearAll()
    }

    @Test
    fun `converts message`() {
        fixture.minimumEventLevel = Level.DEBUG
        val logger = fixture.getSut()
        logger.debug("testing message conversion {}, {}", 1, 2)

        await.untilAsserted {
            verify(fixture.transport).send(check { it: SentryEvent ->
                assertEquals("testing message conversion 1, 2", it.message.formatted)
                assertEquals("testing message conversion {}, {}", it.message.message)
                assertEquals(listOf("1", "2"), it.message.params)
                assertEquals("io.sentry.log4j2.SentryAppenderTest", it.logger)
            })
        }
    }

    @Test
    fun `event date is in UTC`() {
        fixture.minimumEventLevel = Level.DEBUG
        val logger = fixture.getSut()
        val utcTime = LocalDateTime.now(ZoneId.of("UTC"))

        logger.debug("testing event date")

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
        fixture.minimumEventLevel = Level.TRACE
        val logger = fixture.getSut()
        logger.trace("testing trace level")

        await.untilAsserted {
            verify(fixture.transport).send(check { it: SentryEvent ->
                assertEquals(SentryLevel.DEBUG, it.level)
            })
        }
    }

    @Test
    fun `converts debug log level to Sentry level`() {
        fixture.minimumEventLevel = Level.DEBUG
        val logger = fixture.getSut()
        logger.debug("testing debug level")

        await.untilAsserted {
            verify(fixture.transport).send(check { it: SentryEvent ->
                assertEquals(SentryLevel.DEBUG, it.level)
            })
        }
    }

    @Test
    fun `converts info log level to Sentry level`() {
        fixture.minimumEventLevel = Level.INFO
        val logger = fixture.getSut()
        logger.info("testing info level")

        await.untilAsserted {
            verify(fixture.transport).send(check { it: SentryEvent ->
                assertEquals(SentryLevel.INFO, it.level)
            })
        }
    }

    @Test
    fun `converts warn log level to Sentry level`() {
        fixture.minimumEventLevel = Level.WARN
        val logger = fixture.getSut()
        logger.warn("testing warn level")

        await.untilAsserted {
            verify(fixture.transport).send(check { it: SentryEvent ->
                assertEquals(SentryLevel.WARNING, it.level)
            })
        }
    }

    @Test
    fun `converts error log level to Sentry level`() {
        fixture.minimumEventLevel = Level.ERROR
        val logger = fixture.getSut()
        logger.error("testing error level")

        await.untilAsserted {
            verify(fixture.transport).send(check { it: SentryEvent ->
                assertEquals(SentryLevel.ERROR, it.level)
            })
        }
    }

    @Test
    fun `converts fatal log level to Sentry level`() {
        fixture.minimumEventLevel = Level.FATAL
        val logger = fixture.getSut()
        logger.fatal("testing fatal level")

        await.untilAsserted {
            verify(fixture.transport).send(check { it: SentryEvent ->
                assertEquals(SentryLevel.FATAL, it.level)
            })
        }
    }

    @Test
    fun `attaches thread information`() {
        fixture.minimumEventLevel = Level.WARN
        val logger = fixture.getSut()
        logger.warn("testing thread information")

        await.untilAsserted {
            verify(fixture.transport).send(check { it: SentryEvent ->
                assertNotNull(it.getExtra("thread_name"))
            })
        }
    }

    @Test
    fun `sets tags from ThreadContext`() {
        fixture.minimumEventLevel = Level.WARN
        val logger = fixture.getSut()
        ThreadContext.put("key", "value")
        logger.warn("testing MDC tags")

        await.untilAsserted {
            verify(fixture.transport).send(check { it: SentryEvent ->
                assertEquals(mapOf("key" to "value"), it.contexts["Context Data"])
            })
        }
    }

    @Test
    fun `does not create MDC context when no MDC tags are set`() {
        fixture.minimumEventLevel = Level.WARN
        val logger = fixture.getSut()
        logger.warn("testing without MDC tags")

        await.untilAsserted {
            verify(fixture.transport).send(check { it: SentryEvent ->
                assertFalse(it.contexts.containsKey("MDC"))
            })
        }
    }

    @Test
    fun `attaches throwable`() {
        fixture.minimumEventLevel = Level.WARN
        val logger = fixture.getSut()
        val throwable = RuntimeException("something went wrong")
        logger.warn("testing throwable", throwable)

        await.untilAsserted {
            verify(fixture.transport).send(check { it: SentryEvent ->
                assertEquals(throwable, it.throwable)
            })
        }
    }

    @Test
    fun `sets SDK version`() {
        fixture.minimumEventLevel = Level.INFO
        val logger = fixture.getSut()
        logger.info("testing sdk version")

        await.untilAsserted {
            verify(fixture.transport).send(check { it: SentryEvent ->
                assertEquals(BuildConfig.SENTRY_LOG4J2_SDK_NAME, it.sdk.name)
                assertEquals(BuildConfig.VERSION_NAME, it.sdk.version)
                assertNotNull(it.sdk.packages)
                assertTrue(it.sdk.packages!!.any { pkg ->
                    "maven:sentry-log4j2" == pkg.name &&
                        BuildConfig.VERSION_NAME == pkg.version
                })
            })
        }
    }

    @Test
    fun `attaches breadcrumbs with level higher than minimumBreadcrumbLevel`() {
        fixture.minimumEventLevel = Level.WARN
        fixture.minimumBreadcrumbLevel = Level.DEBUG
        val logger = fixture.getSut()
        val utcTime = LocalDateTime.now(ZoneId.of("UTC"))

        logger.debug("this should be a breadcrumb #1")
        logger.info("this should be a breadcrumb #2")
        logger.warn("testing message with breadcrumbs")

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
                assertEquals("io.sentry.log4j2.SentryAppenderTest", breadcrumb.category)
                assertEquals(SentryLevel.DEBUG, breadcrumb.level)
            })
        }
    }

    @Test
    fun `does not attach breadcrumbs with level lower than minimumBreadcrumbLevel`() {
        fixture.minimumEventLevel = Level.WARN
        fixture.minimumBreadcrumbLevel = Level.INFO
        val logger = fixture.getSut()

        logger.debug("this should NOT be a breadcrumb")
        logger.info("this should be a breadcrumb")
        logger.warn("testing message with breadcrumbs")

        await.untilAsserted {
            verify(fixture.transport).send(check { it: SentryEvent ->
                assertEquals(1, it.breadcrumbs.size)
                assertEquals("this should be a breadcrumb", it.breadcrumbs[0].message)
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
            verify(fixture.transport).send(check { it: SentryEvent ->
                assertEquals(2, it.breadcrumbs.size)
                assertEquals("this should be a breadcrumb", it.breadcrumbs[0].message)
                assertEquals("this should not be sent as the event but be a breadcrumb", it.breadcrumbs[1].message)
            })
        }
    }
}
