package io.sentry.log4j2

import io.sentry.ITransportFactory
import io.sentry.InitPriority
import io.sentry.ScopesAdapter
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.SentryLogLevel
import io.sentry.checkEvent
import io.sentry.checkLogs
import io.sentry.test.initForTest
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
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.MarkerManager
import org.apache.logging.log4j.ThreadContext
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.config.AppenderRef
import org.apache.logging.log4j.core.config.Configuration
import org.apache.logging.log4j.core.config.LoggerConfig
import org.apache.logging.log4j.spi.ExtendedLogger
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SentryAppenderTest {
  private class Fixture {
    val loggerContext = LogManager.getContext() as LoggerContext
    var transportFactory = mock<ITransportFactory>()
    var transport = mock<ITransport>()
    val utcTimeZone: ZoneId = ZoneId.of("UTC")

    init {
      whenever(transportFactory.create(any(), any())).thenReturn(transport)
    }

    fun getSut(
      transportFactory: ITransportFactory? = null,
      minimumBreadcrumbLevel: Level? = null,
      minimumEventLevel: Level? = null,
      minimumLevel: Level? = null,
      debug: Boolean? = null,
      contextTags: List<String>? = null,
    ): ExtendedLogger {
      if (transportFactory != null) {
        this.transportFactory = transportFactory
      }
      loggerContext.start()
      val config: Configuration = loggerContext.configuration
      val appender =
        SentryAppender(
          "sentry",
          null,
          "http://key@localhost/proj",
          minimumBreadcrumbLevel,
          minimumEventLevel,
          minimumLevel,
          debug,
          this.transportFactory,
          ScopesAdapter.getInstance(),
          contextTags?.toTypedArray(),
        )
      config.addAppender(appender)

      val ref = AppenderRef.createAppenderRef("sentry", null, null)

      val loggerConfig =
        LoggerConfig.createLogger(
          false,
          Level.TRACE,
          "sentry_logger",
          "true",
          arrayOf(ref),
          null,
          config,
          null,
        )
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
    Sentry.close()
  }

  @Test
  fun `does not initialize Sentry if Sentry is already enabled with higher prio`() {
    initForTest {
      it.dsn = "http://key@localhost/proj"
      it.environment = "manual-environment"
      it.setTransportFactory(fixture.transportFactory)
      it.isEnableBackpressureHandling = false
      it.initPriority = InitPriority.LOW
    }
    val logger = fixture.getSut()
    logger.error("testing environment field")

    verify(fixture.transport)
      .send(
        checkEvent { event -> assertEquals("manual-environment", event.environment) },
        anyOrNull(),
      )
  }

  @Test
  fun `converts message`() {
    val logger = fixture.getSut(minimumEventLevel = Level.DEBUG)
    logger.debug("testing message conversion {}, {}", 1, 2)

    verify(fixture.transport)
      .send(
        checkEvent { event ->
          assertNotNull(event.message) { message ->
            assertEquals("testing message conversion 1, 2", message.formatted)
            assertEquals("testing message conversion {}, {}", message.message)
            assertEquals(listOf("1", "2"), message.params)
          }
          assertEquals("io.sentry.log4j2.SentryAppenderTest", event.logger)
        },
        anyOrNull(),
      )
  }

  @Test
  fun `event date is in UTC`() {
    val logger = fixture.getSut(minimumEventLevel = Level.DEBUG)
    val utcTime = LocalDateTime.now(fixture.utcTimeZone)

    logger.debug("testing event date")

    verify(fixture.transport)
      .send(
        checkEvent { event ->
          val eventTime =
            Instant.ofEpochMilli(event.timestamp.time).atZone(fixture.utcTimeZone).toLocalDateTime()

          assertTrue { eventTime.plusSeconds(1).isAfter(utcTime) }
          assertTrue { eventTime.minusSeconds(1).isBefore(utcTime) }
        },
        anyOrNull(),
      )
  }

  @Test
  fun `converts trace log level to Sentry level`() {
    val logger = fixture.getSut(minimumEventLevel = Level.TRACE)
    logger.trace("testing trace level")

    verify(fixture.transport)
      .send(checkEvent { event -> assertEquals(SentryLevel.DEBUG, event.level) }, anyOrNull())
  }

  @Test
  fun `converts debug log level to Sentry level`() {
    val logger = fixture.getSut(minimumEventLevel = Level.DEBUG)
    logger.debug("testing debug level")

    verify(fixture.transport)
      .send(checkEvent { event -> assertEquals(SentryLevel.DEBUG, event.level) }, anyOrNull())
  }

  @Test
  fun `converts info log level to Sentry level`() {
    val logger = fixture.getSut(minimumEventLevel = Level.INFO)
    logger.info("testing info level")

    verify(fixture.transport)
      .send(checkEvent { event -> assertEquals(SentryLevel.INFO, event.level) }, anyOrNull())
  }

  @Test
  fun `converts warn log level to Sentry level`() {
    val logger = fixture.getSut(minimumEventLevel = Level.WARN)
    logger.warn("testing warn level")

    verify(fixture.transport)
      .send(checkEvent { event -> assertEquals(SentryLevel.WARNING, event.level) }, anyOrNull())
  }

  @Test
  fun `converts error log level to Sentry level`() {
    val logger = fixture.getSut(minimumEventLevel = Level.ERROR)
    logger.error("testing error level")

    verify(fixture.transport)
      .send(checkEvent { event -> assertEquals(SentryLevel.ERROR, event.level) }, anyOrNull())
  }

  @Test
  fun `converts error log level to Sentry level with exception`() {
    val logger = fixture.getSut(minimumEventLevel = Level.ERROR)
    logger.error("testing error level", RuntimeException("test exc"))

    verify(fixture.transport)
      .send(
        checkEvent { event ->
          assertEquals(SentryLevel.ERROR, event.level)
          val exception = event.exceptions!!.first()
          assertEquals(SentryAppender.MECHANISM_TYPE, exception.mechanism!!.type)
          assertEquals("test exc", exception.value)
        },
        anyOrNull(),
      )
  }

  @Test
  fun `converts fatal log level to Sentry level`() {
    val logger = fixture.getSut(minimumEventLevel = Level.FATAL)
    logger.fatal("testing fatal level")

    verify(fixture.transport)
      .send(checkEvent { event -> assertEquals(SentryLevel.FATAL, event.level) }, anyOrNull())
  }

  @Test
  fun `converts trace log level to Sentry log level`() {
    val logger = fixture.getSut(minimumLevel = Level.TRACE)
    logger.trace("testing trace level")

    Sentry.flush(10)

    verify(fixture.transport)
      .send(
        checkLogs { event ->
          assertEquals(SentryLogLevel.TRACE, event.items.first().level)
          assertEquals(
            "auto.log.log4j2",
            event.items.first().attributes?.get("sentry.origin")?.value,
          )
        }
      )
  }

  @Test
  fun `converts debug log level to Sentry log level`() {
    val logger = fixture.getSut(minimumLevel = Level.DEBUG)
    logger.debug("testing debug level")

    Sentry.flush(10)

    verify(fixture.transport)
      .send(checkLogs { event -> assertEquals(SentryLogLevel.DEBUG, event.items.first().level) })
  }

  @Test
  fun `converts info log level to Sentry log level`() {
    val logger = fixture.getSut(minimumLevel = Level.INFO)
    logger.info("testing info level")

    Sentry.flush(10)

    verify(fixture.transport)
      .send(checkLogs { event -> assertEquals(SentryLogLevel.INFO, event.items.first().level) })
  }

  @Test
  fun `converts warn log level to Sentry log level`() {
    val logger = fixture.getSut(minimumLevel = Level.WARN)
    logger.warn("testing warn level")

    Sentry.flush(10)

    verify(fixture.transport)
      .send(checkLogs { event -> assertEquals(SentryLogLevel.WARN, event.items.first().level) })
  }

  @Test
  fun `converts error log level to Sentry log level`() {
    val logger = fixture.getSut(minimumLevel = Level.ERROR)
    logger.error("testing error level")

    Sentry.flush(10)

    verify(fixture.transport)
      .send(checkLogs { event -> assertEquals(SentryLogLevel.ERROR, event.items.first().level) })
  }

  @Test
  fun `converts fatal log level to Sentry log level`() {
    val logger = fixture.getSut(minimumLevel = Level.FATAL)
    logger.fatal("testing fatal level")

    Sentry.flush(10)

    verify(fixture.transport)
      .send(checkLogs { event -> assertEquals(SentryLogLevel.FATAL, event.items.first().level) })
  }

  @Test
  fun `attaches thread information`() {
    val logger = fixture.getSut(minimumEventLevel = Level.WARN)
    logger.warn("testing thread information")

    verify(fixture.transport)
      .send(checkEvent { event -> assertNotNull(event.getExtra("thread_name")) }, anyOrNull())
  }

  @Test
  fun `sets tags from ThreadContext`() {
    val logger = fixture.getSut(minimumEventLevel = Level.WARN)
    ThreadContext.put("key", "value")
    logger.warn("testing MDC tags")

    verify(fixture.transport)
      .send(
        checkEvent { event ->
          assertEquals(mapOf("key" to "value"), event.contexts["Context Data"])
        },
        anyOrNull(),
      )
  }

  @Test
  fun `sets tags from ThreadContext as Sentry tags`() {
    val logger = fixture.getSut(minimumEventLevel = Level.WARN, contextTags = listOf("contextTag1"))
    ThreadContext.put("key", "value")
    ThreadContext.put("contextTag1", "contextTag1Value")
    logger.warn("testing MDC tags")

    verify(fixture.transport)
      .send(
        checkEvent { event ->
          assertEquals(mapOf("key" to "value"), event.contexts["Context Data"])
          assertEquals(mapOf("contextTag1" to "contextTag1Value"), event.tags)
        },
        anyOrNull(),
      )
  }

  @Test
  fun `ignore set tags with null values from ThreadContext`() {
    val logger = fixture.getSut(minimumEventLevel = Level.WARN)
    ThreadContext.put("key1", null)
    ThreadContext.put("key2", "value")
    logger.warn("testing MDC tags")

    verify(fixture.transport)
      .send(
        checkEvent { event ->
          assertNotNull(event.contexts["Context Data"]) {
            val contextData = it as Map<*, *>
            assertNull(contextData["key1"])
            assertEquals("value", contextData["key2"])
          }
        },
        anyOrNull(),
      )
  }

  @Test
  fun `does not set context data if all context entries are null`() {
    val logger = fixture.getSut(minimumEventLevel = Level.WARN)
    ThreadContext.put("key1", null)
    ThreadContext.put("key2", null)
    logger.warn("testing MDC tags")

    verify(fixture.transport)
      .send(checkEvent { event -> assertNull(event.contexts["Context Data"]) }, anyOrNull())
  }

  @Test
  fun `does not create MDC context when no MDC tags are set`() {
    val logger = fixture.getSut(minimumEventLevel = Level.WARN)
    logger.warn("testing without MDC tags")

    verify(fixture.transport)
      .send(checkEvent { event -> assertFalse(event.contexts.containsKey("MDC")) }, anyOrNull())
  }

  @Test
  fun `attaches marker information`() {
    val logger = fixture.getSut(minimumEventLevel = Level.WARN)
    val sqlMarker =
      MarkerManager.getMarker("SQL")
        .setParents(MarkerManager.getMarker("SQL_QUERY"), MarkerManager.getMarker("SQL_UPDATE"))

    logger.warn(sqlMarker, "testing marker tags")

    verify(fixture.transport)
      .send(
        checkEvent { event ->
          assertEquals("SQL[ SQL_QUERY, SQL_UPDATE ]", event.getExtra("marker"))
        },
        anyOrNull(),
      )
  }

  @Test
  fun `sets SDK version`() {
    val logger = fixture.getSut(minimumEventLevel = Level.INFO)
    logger.info("testing sdk version")

    verify(fixture.transport)
      .send(
        checkEvent { event ->
          assertNotNull(event.sdk) {
            assertEquals(BuildConfig.SENTRY_LOG4J2_SDK_NAME, it.name)
            assertEquals(BuildConfig.VERSION_NAME, it.version)
            assertTrue(
              it.packageSet.any { pkg ->
                "maven:io.sentry:sentry-log4j2" == pkg.name &&
                  BuildConfig.VERSION_NAME == pkg.version
              }
            )
            assertTrue(it.integrationSet.contains("Log4j"))
          }
        },
        anyOrNull(),
      )
  }

  @Test
  fun `attaches breadcrumbs with level higher than minimumBreadcrumbLevel`() {
    val logger =
      fixture.getSut(minimumEventLevel = Level.WARN, minimumBreadcrumbLevel = Level.DEBUG)
    val utcTime = LocalDateTime.now(fixture.utcTimeZone)

    logger.debug("this should be a breadcrumb #1")
    logger.info("this should be a breadcrumb #2")
    logger.warn("testing message with breadcrumbs")

    verify(fixture.transport)
      .send(
        checkEvent { event ->
          assertNotNull(event.breadcrumbs) { breadcrumbs ->
            assertEquals(2, breadcrumbs.size)
            val breadcrumb = breadcrumbs[0]
            val breadcrumbTime =
              Instant.ofEpochMilli(event.timestamp.time)
                .atZone(fixture.utcTimeZone)
                .toLocalDateTime()
            assertTrue { breadcrumbTime.plusSeconds(1).isAfter(utcTime) }
            assertTrue { breadcrumbTime.minusSeconds(1).isBefore(utcTime) }
            assertEquals("this should be a breadcrumb #1", breadcrumb.message)
            assertEquals("io.sentry.log4j2.SentryAppenderTest", breadcrumb.category)
            assertEquals(SentryLevel.DEBUG, breadcrumb.level)
          }
        },
        anyOrNull(),
      )
  }

  @Test
  fun `does not attach breadcrumbs with level lower than minimumBreadcrumbLevel`() {
    val logger = fixture.getSut(minimumEventLevel = Level.WARN, minimumBreadcrumbLevel = Level.INFO)

    logger.debug("this should NOT be a breadcrumb")
    logger.info("this should be a breadcrumb")
    logger.warn("testing message with breadcrumbs")

    verify(fixture.transport)
      .send(
        checkEvent { event ->
          assertNotNull(event.breadcrumbs) { breadcrumbs ->
            assertEquals(1, breadcrumbs.size)
            assertEquals("this should be a breadcrumb", breadcrumbs[0].message)
          }
        },
        anyOrNull(),
      )
  }

  @Test
  fun `attaches breadcrumbs for default appender configuration`() {
    val logger = fixture.getSut()

    logger.debug("this should not be a breadcrumb as the level is lower than the minimum INFO")
    logger.info("this should be a breadcrumb")
    logger.warn("this should not be sent as the event but be a breadcrumb")
    logger.error("this should be sent as the event")

    verify(fixture.transport)
      .send(
        checkEvent { event ->
          assertNotNull(event.breadcrumbs) { breadcrumbs ->
            assertEquals(2, breadcrumbs.size)
            assertEquals("this should be a breadcrumb", breadcrumbs[0].message)
            assertEquals(
              "this should not be sent as the event but be a breadcrumb",
              breadcrumbs[1].message,
            )
          }
        },
        anyOrNull(),
      )
  }

  @Test
  fun `uses options set in properties file`() {
    val logger = fixture.getSut()
    logger.error("some event")

    verify(fixture.transport)
      .send(
        checkEvent { event -> assertEquals("release from sentry.properties", event.release) },
        anyOrNull(),
      )
  }

  @Test
  fun `sets the debug mode`() {
    fixture.getSut(debug = true)
    assertTrue(ScopesAdapter.getInstance().options.isDebug)
  }

  @Test
  fun `does not set template on log when logging message without parameters`() {
    val logger = fixture.getSut(minimumLevel = Level.ERROR)
    logger.error("testing message without parameters")

    Sentry.flush(1000)

    verify(fixture.transport)
      .send(
        checkLogs { logs ->
          val log = logs.items.first()
          assertEquals("testing message without parameters", log.body)
          assertNull(log.attributes?.get("sentry.message.template"))
        }
      )
  }

  @Test
  fun `sets template on log when logging message with parameters`() {
    val logger = fixture.getSut(minimumLevel = Level.ERROR)
    logger.error("testing message {}", "param")

    Sentry.flush(1000)

    verify(fixture.transport)
      .send(
        checkLogs { logs ->
          val log = logs.items.first()
          assertEquals("testing message param", log.body)
          assertEquals("testing message {}", log.attributes?.get("sentry.message.template")?.value)
          assertEquals("param", log.attributes?.get("sentry.message.parameter.0")?.value)
        }
      )
  }

  @Test
  fun `sets template on log when logging message with parameters and number of parameters is wrong`() {
    val logger = fixture.getSut(minimumLevel = Level.ERROR)
    logger.error("testing message {} {} {}", "param1", "param2")

    Sentry.flush(1000)

    verify(fixture.transport)
      .send(
        checkLogs { logs ->
          val log = logs.items.first()
          assertEquals("testing message param1 param2 {}", log.body)
          assertEquals(
            "testing message {} {} {}",
            log.attributes?.get("sentry.message.template")?.value,
          )
          assertEquals("param1", log.attributes?.get("sentry.message.parameter.0")?.value)
          assertEquals("param2", log.attributes?.get("sentry.message.parameter.1")?.value)
          assertNull(log.attributes?.get("sentry.message.parameter.2"))
        }
      )
  }

  @Test
  fun `sets properties from ThreadContext as attributes on logs`() {
    val logger = fixture.getSut(minimumLevel = Level.INFO, contextTags = listOf("someTag"))

    ThreadContext.put("someTag", "someValue")
    ThreadContext.put("otherTag", "otherValue")
    logger.info("testing MDC properties in logs")

    Sentry.flush(1000)

    verify(fixture.transport)
      .send(
        checkLogs { logs ->
          val log = logs.items.first()
          assertEquals("testing MDC properties in logs", log.body)
          val attributes = log.attributes!!
          assertEquals("someValue", attributes["mdc.someTag"]?.value)
          assertNull(attributes["otherTag"])
        }
      )
  }
}
