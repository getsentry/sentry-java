package io.sentry.jul

import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.checkEvent
import io.sentry.transport.ITransport
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.slf4j.MDC
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SentryHandlerTest {
    private class Fixture(minimumBreadcrumbLevel: Level? = null, minimumEventLevel: Level? = null, val configureWithLogManager: Boolean = false, val transport: ITransport = mock(), contextTags: List<String>? = null) {
        var logger: Logger
        var handler: SentryHandler

        init {
            val options = SentryOptions()
            options.dsn = "http://key@localhost/proj"
            options.setTransportFactory { _, _ -> transport }
            contextTags?.forEach { options.addContextTag(it) }
            logger = Logger.getLogger("jul.SentryHandlerTest")
            handler = SentryHandler(options, configureWithLogManager)
            handler.setMinimumBreadcrumbLevel(minimumBreadcrumbLevel)
            handler.setMinimumEventLevel(minimumEventLevel)
            handler.level = Level.ALL
            logger.handlers.forEach { logger.removeHandler(it) }
            logger.addHandler(handler)
            logger.level = Level.ALL
        }
    }

    private lateinit var fixture: Fixture

    @BeforeTest
    fun `clear MDC`() {
        MDC.clear()
    }

    @AfterTest
    fun `close Sentry`() {
        Sentry.close()
    }

    @Test
    fun `does not initialize Sentry if Sentry is already enabled`() {
        val transport = mock<ITransport>()
        Sentry.init {
            it.dsn = "http://key@localhost/proj"
            it.environment = "manual-environment"
            it.setTransportFactory { _, _ -> transport }
        }
        fixture = Fixture(transport = transport)
        fixture.logger.severe("testing environment field")

        verify(fixture.transport).send(
            checkEvent { event ->
                assertEquals("manual-environment", event.environment)
            },
            anyOrNull()
        )
    }

    @Test
    fun `converts message`() {
        fixture = Fixture(minimumEventLevel = Level.SEVERE)
        fixture.logger.log(Level.SEVERE, "testing message conversion {0}, {1}", arrayOf(1, 2))

        verify(fixture.transport).send(
            checkEvent { event ->
                assertNotNull(event.message) { message ->
                    assertEquals("testing message conversion 1, 2", message.formatted)
                    assertEquals("testing message conversion {0}, {1}", message.message)
                    assertEquals(listOf("1", "2"), message.params)
                }
                assertEquals("jul.SentryHandlerTest", event.logger)
            },
            anyOrNull()
        )
    }

    @Test
    fun `converts message with printf style enabled`() {
        fixture = Fixture(minimumEventLevel = Level.SEVERE)
        fixture.logger.log(Level.SEVERE, "testing message conversion {0}, {1}", arrayOf(1, 2))

        verify(fixture.transport).send(
            checkEvent { event ->
                assertNotNull(event.message) { message ->
                    assertEquals("testing message conversion 1, 2", message.formatted)
                    assertEquals("testing message conversion {0}, {1}", message.message)
                    assertEquals(listOf("1", "2"), message.params)
                }
                assertEquals("jul.SentryHandlerTest", event.logger)
            },
            anyOrNull()
        )
    }

    @Test
    fun `event date is in UTC`() {
        fixture = Fixture(minimumEventLevel = Level.CONFIG)
        val utcTime = LocalDateTime.now(ZoneId.of("UTC"))

        fixture.logger.config("testing event date")

        verify(fixture.transport).send(
            checkEvent { event ->
                val eventTime = Instant.ofEpochMilli(event.timestamp.time)
                    .atZone(ZoneId.of("UTC"))
                    .toLocalDateTime()

                assertTrue { eventTime.plusSeconds(1).isAfter(utcTime) }
                assertTrue { eventTime.minusSeconds(1).isBefore(utcTime) }
            },
            anyOrNull()
        )
    }

    @Test
    fun `converts fine log level to Sentry level`() {
        fixture = Fixture(minimumEventLevel = Level.FINE)
        fixture.logger.fine("testing trace level")

        verify(fixture.transport).send(
            checkEvent { event ->
                assertEquals(SentryLevel.DEBUG, event.level)
            },
            anyOrNull()
        )
    }

    @Test
    fun `converts config log level to Sentry level`() {
        fixture = Fixture(minimumEventLevel = Level.CONFIG)
        fixture.logger.config("testing debug level")

        verify(fixture.transport).send(
            checkEvent { event ->
                assertEquals(SentryLevel.DEBUG, event.level)
            },
            anyOrNull()
        )
    }

    @Test
    fun `converts info log level to Sentry level`() {
        fixture = Fixture(minimumEventLevel = Level.INFO)
        fixture.logger.info("testing info level")

        verify(fixture.transport).send(
            checkEvent { event ->
                assertEquals(SentryLevel.INFO, event.level)
            },
            anyOrNull()
        )
    }

    @Test
    fun `converts warn log level to Sentry level`() {
        fixture = Fixture(minimumEventLevel = Level.WARNING)
        fixture.logger.warning("testing warn level")

        verify(fixture.transport).send(
            checkEvent { event ->
                assertEquals(SentryLevel.WARNING, event.level)
            },
            anyOrNull()
        )
    }

    @Test
    fun `converts severe log level to Sentry level`() {
        fixture = Fixture(minimumEventLevel = Level.SEVERE)
        fixture.logger.severe("testing error level")

        verify(fixture.transport).send(
            checkEvent { event ->
                assertEquals(SentryLevel.ERROR, event.level)
            },
            anyOrNull()
        )
    }

    @Test
    fun `attaches thread information`() {
        fixture = Fixture(minimumEventLevel = Level.WARNING)
        fixture.logger.warning("testing thread information")

        verify(fixture.transport).send(
            checkEvent { event ->
                assertNotNull(event.getExtra("thread_id"))
            },
            anyOrNull()
        )
    }

    @Test
    fun `attaches breadcrumbs with level higher than minimumBreadcrumbLevel`() {
        fixture = Fixture(minimumBreadcrumbLevel = Level.CONFIG, minimumEventLevel = Level.WARNING)
        val utcTime = LocalDateTime.now(ZoneId.of("UTC"))

        fixture.logger.config("this should be a breadcrumb #1")
        fixture.logger.info("this should be a breadcrumb #2")
        fixture.logger.warning("testing message with breadcrumbs")

        verify(fixture.transport).send(
            checkEvent { event ->
                assertNotNull(event.breadcrumbs) { breadcrumbs ->
                    assertEquals(2, breadcrumbs.size)
                    val breadcrumb = breadcrumbs[0]
                    val breadcrumbTime = Instant.ofEpochMilli(event.timestamp.time)
                        .atZone(ZoneId.of("UTC"))
                        .toLocalDateTime()
                    assertTrue { breadcrumbTime.plusSeconds(1).isAfter(utcTime) }
                    assertTrue { breadcrumbTime.minusSeconds(1).isBefore(utcTime) }
                    assertEquals("this should be a breadcrumb #1", breadcrumb.message)
                    assertEquals("jul.SentryHandlerTest", breadcrumb.category)
                    assertEquals(SentryLevel.DEBUG, breadcrumb.level)
                }
            },
            anyOrNull()
        )
    }

    @Test
    fun `does not attach breadcrumbs with level lower than minimumBreadcrumbLevel`() {
        fixture = Fixture(minimumBreadcrumbLevel = Level.INFO, minimumEventLevel = Level.WARNING)

        fixture.logger.config("this should NOT be a breadcrumb")
        fixture.logger.info("this should be a breadcrumb")
        fixture.logger.warning("testing message with breadcrumbs")

        verify(fixture.transport).send(
            checkEvent { event ->
                assertNotNull(event.breadcrumbs) { breadcrumbs ->
                    assertEquals(1, breadcrumbs.size)
                    assertEquals("this should be a breadcrumb", breadcrumbs[0].message)
                }
            },
            anyOrNull()
        )
    }

    @Test
    fun `attaches breadcrumbs for default appender configuration`() {
        fixture = Fixture()

        fixture.logger.config("this should not be a breadcrumb as the level is lower than the minimum INFO")
        fixture.logger.info("this should be a breadcrumb")
        fixture.logger.warning("this should not be sent as the event but be a breadcrumb")
        fixture.logger.severe("this should be sent as the event")

        verify(fixture.transport).send(
            checkEvent { event ->
                assertNotNull(event.breadcrumbs) { breadcrumbs ->
                    assertEquals(2, breadcrumbs.size)
                    assertEquals("this should be a breadcrumb", breadcrumbs[0].message)
                    assertEquals("this should not be sent as the event but be a breadcrumb", breadcrumbs[1].message)
                }
            },
            anyOrNull()
        )
    }

    @Test
    fun `uses options set in properties file`() {
        fixture = Fixture()
        fixture.logger.severe("some event")

        verify(fixture.transport).send(
            checkEvent { event ->
                assertEquals("release from sentry.properties", event.release)
            },
            anyOrNull()
        )
    }

    @Test
    fun `fetches configuration from logging dot properties`() {
        fixture = Fixture(configureWithLogManager = true)
        assertEquals(Level.CONFIG, fixture.handler.minimumBreadcrumbLevel)
        assertEquals(Level.WARNING, fixture.handler.minimumEventLevel)
        assertEquals(Level.ALL, fixture.handler.level)
        assertTrue(fixture.handler.isPrintfStyle)
    }

    @Test
    fun `sets tags from MDC`() {
        fixture = Fixture(minimumEventLevel = Level.WARNING)
        MDC.put("key", "value")
        fixture.logger.warning("testing MDC tags")

        verify(fixture.transport).send(
            checkEvent { event ->
                assertEquals(mapOf("key" to "value"), event.contexts["MDC"])
            },
            anyOrNull()
        )
    }

    @Test
    fun `sets tags as Sentry tags from MDC`() {
        fixture = Fixture(minimumEventLevel = Level.WARNING, contextTags = listOf("contextTag1"))
        MDC.put("key", "value")
        MDC.put("contextTag1", "contextTag1Value")
        fixture.logger.warning("testing MDC tags")

        verify(fixture.transport).send(
            checkEvent { event ->
                assertEquals(mapOf("key" to "value"), event.contexts["MDC"])
                assertEquals(mapOf("contextTag1" to "contextTag1Value"), event.tags)
            },
            anyOrNull()
        )
    }

    @Test
    fun `ignore set tags with null values from MDC`() {
        fixture = Fixture(minimumEventLevel = Level.WARNING)
        MDC.put("key1", null)
        MDC.put("key2", "value")
        fixture.logger.warning("testing MDC tags")

        verify(fixture.transport).send(
            checkEvent { event ->
                assertNotNull(event.contexts["MDC"]) {
                    val contextData = it as Map<*, *>
                    assertNull(contextData["key1"])
                    assertEquals("value", contextData["key2"])
                }
            },
            anyOrNull()
        )
    }

    @Test
    fun `does not set MDC if all context entries are null`() {
        fixture = Fixture(minimumEventLevel = Level.WARNING)
        MDC.put("key1", null)
        MDC.put("key2", null)
        fixture.logger.warning("testing MDC tags")

        verify(fixture.transport).send(
            checkEvent { event ->
                assertNull(event.contexts["MDC"])
            },
            anyOrNull()
        )
    }

    @Test
    fun `does not create MDC context when no MDC tags are set`() {
        fixture = Fixture(minimumEventLevel = Level.WARNING)
        fixture.logger.warning("testing without MDC tags")

        verify(fixture.transport).send(
            checkEvent { event ->
                assertFalse(event.contexts.containsKey("MDC"))
            },
            anyOrNull()
        )
    }

    @Test
    fun `sets SDK version`() {
        fixture = Fixture(minimumEventLevel = Level.INFO)
        fixture.logger.info("testing sdk version")

        verify(fixture.transport).send(
            checkEvent { event ->
                assertNotNull(event.sdk) {
                    assertEquals(BuildConfig.SENTRY_JUL_SDK_NAME, it.name)
                    assertEquals(BuildConfig.VERSION_NAME, it.version)
                    assertTrue(
                        it.packageSet.any { pkg ->
                            "maven:io.sentry:sentry-jul" == pkg.name &&
                                BuildConfig.VERSION_NAME == pkg.version
                        }
                    )
                    assertTrue(
                        it.integrationSet.contains("Jul")
                    )
                }
            },
            anyOrNull()
        )
    }
}
