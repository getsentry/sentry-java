package io.sentry.jul

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.BuildConfig
import io.sentry.GsonSerializer
import io.sentry.NoOpLogger
import io.sentry.Sentry
import io.sentry.SentryEnvelope
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.transport.ITransport
import io.sentry.transport.TransportResult
import org.awaitility.kotlin.await
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SentryHandlerTest {
    private class Fixture(minimumBreadcrumbLevel: Level? = null, minimumEventLevel: Level? = null, val transport: ITransport = mock<ITransport>()) {
        lateinit var logger: Logger

        init {
            whenever(transport.send(any())).thenReturn(TransportResult.success())
            val options = SentryOptions()
            options.dsn = "http://key@localhost/proj"

            logger = Logger.getLogger(SentryHandlerTest::class.java.canonicalName)
        }
    }

    private lateinit var fixture: Fixture

    @AfterTest
    fun `stop logback`() {
        Sentry.close()
    }

    @Test
    fun `does not initialize Sentry if Sentry is already enabled`() {
        val transport = mock<ITransport>()
        Sentry.init {
            it.dsn = "http://key@localhost/proj"
            it.environment = "manual-environment"
            it.setTransport(transport)
        }
        fixture = Fixture(transport = transport)
        fixture.logger.severe("testing environment field")

        await.untilAsserted {
            verify(fixture.transport).send(checkEvent { event ->
                assertEquals("manual-environment", event.environment)
            })
        }
    }

    @Test
    fun `converts message`() {
        fixture = Fixture(minimumEventLevel = Level.SEVERE)
        fixture.logger.log(Level.SEVERE, "testing message conversion {}, {}", arrayOf(1, 2))

        await.untilAsserted {
            verify(fixture.transport).send(checkEvent { event ->
                assertEquals("testing message conversion 1, 2", event.message.formatted)
                assertEquals("testing message conversion {}, {}", event.message.message)
                assertEquals(listOf("1", "2"), event.message.params)
                assertEquals("io.sentry.logback.SentryAppenderTest", event.logger)
            })
        }
    }

    @Test
    fun `event date is in UTC`() {
        fixture = Fixture(minimumEventLevel = Level.CONFIG)
        val utcTime = LocalDateTime.now(ZoneId.of("UTC"))

        fixture.logger.config("testing event date")

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
        fixture = Fixture(minimumEventLevel = Level.FINE)
        fixture.logger.fine("testing trace level")

        await.untilAsserted {
            verify(fixture.transport).send(checkEvent { event ->
                assertEquals(SentryLevel.DEBUG, event.level)
            })
        }
    }

    @Test
    fun `converts debug log level to Sentry level`() {
        fixture = Fixture(minimumEventLevel = Level.CONFIG)
        fixture.logger.config("testing debug level")

        await.untilAsserted {
            verify(fixture.transport).send(checkEvent { event ->
                assertEquals(SentryLevel.DEBUG, event.level)
            })
        }
    }

    @Test
    fun `converts info log level to Sentry level`() {
        fixture = Fixture(minimumEventLevel = Level.INFO)
        fixture.logger.info("testing info level")

        await.untilAsserted {
            verify(fixture.transport).send(checkEvent { event ->
                assertEquals(SentryLevel.INFO, event.level)
            })
        }
    }

    @Test
    fun `converts warn log level to Sentry level`() {
        fixture = Fixture(minimumEventLevel = Level.WARNING)
        fixture.logger.warning("testing warn level")

        await.untilAsserted {
            verify(fixture.transport).send(checkEvent { event ->
                assertEquals(SentryLevel.WARNING, event.level)
            })
        }
    }

    @Test
    fun `converts error log level to Sentry level`() {
        fixture = Fixture(minimumEventLevel = Level.SEVERE)
        fixture.logger.severe("testing error level")

        await.untilAsserted {
            verify(fixture.transport).send(checkEvent { event ->
                assertEquals(SentryLevel.ERROR, event.level)
            })
        }
    }

    @Test
    fun `attaches thread information`() {
        fixture = Fixture(minimumEventLevel = Level.WARNING)
        fixture.logger.warning("testing thread information")

        await.untilAsserted {
            verify(fixture.transport).send(checkEvent { event ->
                assertNotNull(event.getExtra("thread_name"))
            })
        }
    }

    @Test
    fun `sets SDK version`() {
        fixture = Fixture(minimumEventLevel = Level.INFO)
        fixture.logger.info("testing sdk version")

        await.untilAsserted {
            verify(fixture.transport).send(checkEvent { event ->
                assertEquals(BuildConfig.SENTRY_JAVA_SDK_NAME, event.sdk.name)
                assertEquals(BuildConfig.VERSION_NAME, event.sdk.version)
                assertNotNull(event.sdk.packages)
                assertTrue(event.sdk.packages!!.any { pkg ->
                    "maven:sentry-logback" == pkg.name &&
                        BuildConfig.VERSION_NAME == pkg.version
                })
            })
        }
    }

    @Test
    fun `attaches breadcrumbs with level higher than minimumBreadcrumbLevel`() {
        fixture = Fixture(minimumBreadcrumbLevel = Level.CONFIG, minimumEventLevel = Level.WARNING)
        val utcTime = LocalDateTime.now(ZoneId.of("UTC"))

        fixture.logger.config("this should be a breadcrumb #1")
        fixture.logger.info("this should be a breadcrumb #2")
        fixture.logger.warning("testing message with breadcrumbs")

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
                assertEquals("io.sentry.logback.SentryAppenderTest", breadcrumb.category)
                assertEquals(SentryLevel.DEBUG, breadcrumb.level)
            })
        }
    }

    @Test
    fun `does not attach breadcrumbs with level lower than minimumBreadcrumbLevel`() {
        fixture = Fixture(minimumBreadcrumbLevel = Level.INFO, minimumEventLevel = Level.WARNING)

        fixture.logger.config("this should NOT be a breadcrumb")
        fixture.logger.info("this should be a breadcrumb")
        fixture.logger.warning("testing message with breadcrumbs")

        await.untilAsserted {
            verify(fixture.transport).send(checkEvent { event ->
                assertEquals(1, event.breadcrumbs.size)
                assertEquals("this should be a breadcrumb", event.breadcrumbs[0].message)
            })
        }
    }

    @Test
    fun `attaches breadcrumbs for default appender configuration`() {
        fixture = Fixture()

        fixture.logger.config("this should not be a breadcrumb as the level is lower than the minimum INFO")
        fixture.logger.info("this should be a breadcrumb")
        fixture.logger.warning("this should not be sent as the event but be a breadcrumb")
        fixture.logger.severe("this should be sent as the event")

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
        fixture = Fixture()
        fixture.logger.severe("some event")

        await.untilAsserted {
            verify(fixture.transport).send(checkEvent { event ->
                assertEquals("release from sentry.properties", event.release)
            })
        }
    }
}

inline fun checkEvent(noinline predicate: (SentryEvent) -> Unit): SentryEnvelope {
    val options = SentryOptions().apply {
        setSerializer(GsonSerializer(NoOpLogger.getInstance(), envelopeReader))
    }
    return com.nhaarman.mockitokotlin2.check {
        val event = it.items.first().getEvent(options.serializer)!!
        predicate(event)
    }
}
