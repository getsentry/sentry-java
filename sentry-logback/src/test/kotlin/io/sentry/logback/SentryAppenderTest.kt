package io.sentry.logback

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.LoggingEvent
import ch.qos.logback.classic.spi.LoggingEventVO
import ch.qos.logback.classic.spi.ThrowableProxy
import ch.qos.logback.core.encoder.Encoder
import ch.qos.logback.core.encoder.EncoderBase
import ch.qos.logback.core.status.Status
import io.sentry.ITransportFactory
import io.sentry.InitPriority
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.SentryLogLevel
import io.sentry.SentryOptions
import io.sentry.checkEvent
import io.sentry.checkLogs
import io.sentry.test.applyTestOptions
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
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.slf4j.MarkerFactory

class SentryAppenderTest {
  private class Fixture(
    dsn: String? = "http://key@localhost/proj",
    minimumBreadcrumbLevel: Level? = null,
    minimumEventLevel: Level? = null,
    minimumLevel: Level? = null,
    contextTags: List<String>? = null,
    encoder: Encoder<ILoggingEvent>? = null,
    sendDefaultPii: Boolean = false,
    enableLogs: Boolean = false,
    options: SentryOptions = SentryOptions(),
    startLater: Boolean = false,
  ) {
    val logger: Logger = LoggerFactory.getLogger(SentryAppenderTest::class.java)
    val loggerContext = LoggerFactory.getILoggerFactory() as LoggerContext
    val transportFactory = mock<ITransportFactory>()
    val transport = mock<ITransport>()
    val utcTimeZone: ZoneId = ZoneId.of("UTC")
    val appender = SentryAppender()
    var encoder: Encoder<ILoggingEvent>? = null

    init {
      whenever(this.transportFactory.create(any(), any())).thenReturn(transport)
      this.encoder = encoder
      options.dsn = dsn
      options.isSendDefaultPii = sendDefaultPii
      options.logs.isEnabled = enableLogs
      applyTestOptions(options)
      contextTags?.forEach { options.addContextTag(it) }
      appender.setOptions(options)
      appender.setMinimumBreadcrumbLevel(minimumBreadcrumbLevel)
      appender.setMinimumEventLevel(minimumEventLevel)
      appender.setMinimumLevel(minimumLevel)
      appender.context = loggerContext
      appender.setTransportFactory(transportFactory)
      encoder?.context = loggerContext
      appender.setEncoder(encoder)
      val rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME)
      rootLogger.level = Level.TRACE
      rootLogger.addAppender(appender)
      if (!startLater) {
        start()
      }
    }

    fun start() {
      appender.start()
      encoder?.start()
      loggerContext.start()
    }
  }

  private lateinit var fixture: Fixture

  @AfterTest
  fun `stop logback`() {
    fixture.loggerContext.statusManager.clear()
    fixture.loggerContext.stop()
    Sentry.close()
  }

  @BeforeTest
  fun `clear MDC`() {
    MDC.clear()
    Sentry.close()
  }

  @Test
  fun `does not initialize Sentry if Sentry is already enabled with higher prio`() {
    fixture =
      Fixture(
        startLater = true,
        options =
          SentryOptions().also { it.setTag("only-present-if-logger-init-was-run", "another-value") },
      )
    initForTest {
      it.dsn = "http://key@localhost/proj"
      it.environment = "manual-environment"
      it.setTransportFactory(fixture.transportFactory)
      it.setTag("tag-from-first-init", "some-value")
      it.isEnableBackpressureHandling = false
      it.initPriority = InitPriority.LOW
    }
    fixture.start()

    fixture.logger.error("testing environment field")

    verify(fixture.transport)
      .send(
        checkEvent { event -> assertNull(event.tags?.get("only-present-if-logger-init-was-run")) },
        anyOrNull(),
      )
  }

  @Test
  fun `converts message`() {
    fixture = Fixture(minimumEventLevel = Level.DEBUG)
    fixture.logger.debug("testing message conversion {}, {}", 1, 2)

    verify(fixture.transport)
      .send(
        checkEvent { event ->
          assertNotNull(event.message) { message ->
            assertEquals("testing message conversion 1, 2", message.formatted)
            assertEquals("testing message conversion {}, {}", message.message)
            assertEquals(listOf("1", "2"), message.params)
          }
          assertEquals("io.sentry.logback.SentryAppenderTest", event.logger)
        },
        anyOrNull(),
      )
  }

  @Test
  fun `encodes message`() {
    var encoder = PatternLayoutEncoder()
    encoder.pattern = "encoderadded %msg"
    fixture = Fixture(minimumEventLevel = Level.DEBUG, encoder = encoder, sendDefaultPii = true)
    fixture.logger.info("testing encoding {}", "param1")

    verify(fixture.transport)
      .send(
        checkEvent { event ->
          assertNotNull(event.message) { message ->
            assertEquals("encoderadded testing encoding param1", message.formatted)
            assertEquals("testing encoding {}", message.message)
            assertEquals(listOf("param1"), message.params)
          }
          assertEquals("io.sentry.logback.SentryAppenderTest", event.logger)
        },
        anyOrNull(),
      )
  }

  @Test
  fun `if encoder is set treats raw message and params as PII`() {
    var encoder = PatternLayoutEncoder()
    encoder.pattern = "encoderadded %msg"
    fixture = Fixture(minimumEventLevel = Level.DEBUG, encoder = encoder, sendDefaultPii = false)
    fixture.logger.info("testing encoding {}", "param1")

    verify(fixture.transport)
      .send(
        checkEvent { event ->
          assertNotNull(event.message) { message ->
            assertEquals("encoderadded testing encoding param1", message.formatted)
            assertNull(message.message)
            assertNull(message.params)
          }
          assertEquals("io.sentry.logback.SentryAppenderTest", event.logger)
        },
        anyOrNull(),
      )
  }

  class ThrowingEncoder : EncoderBase<ILoggingEvent> {
    constructor() : super()

    override fun headerBytes(): ByteArray {
      TODO("Not yet implemented")
    }

    override fun footerBytes(): ByteArray {
      TODO("Not yet implemented")
    }

    override fun encode(event: ILoggingEvent?): ByteArray {
      TODO("Not yet implemented")
    }
  }

  @Test
  fun `fallsback when encoder throws`() {
    var encoder = ThrowingEncoder()
    fixture = Fixture(minimumEventLevel = Level.DEBUG, encoder = encoder, sendDefaultPii = true)
    fixture.logger.info("testing when encoder throws")

    verify(fixture.transport)
      .send(
        checkEvent { event ->
          assertNotNull(event.message) { message ->
            assertEquals("testing when encoder throws", message.formatted)
            assertEquals("testing when encoder throws", message.message)
          }
          assertEquals("io.sentry.logback.SentryAppenderTest", event.logger)
        },
        anyOrNull(),
      )
  }

  @Test
  fun `event date is in UTC`() {
    fixture = Fixture(minimumEventLevel = Level.DEBUG)
    val utcTime = LocalDateTime.now(fixture.utcTimeZone)

    fixture.logger.debug("testing event date")

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
    fixture = Fixture(minimumEventLevel = Level.TRACE)
    fixture.logger.trace("testing trace level")

    verify(fixture.transport)
      .send(checkEvent { event -> assertEquals(SentryLevel.DEBUG, event.level) }, anyOrNull())
  }

  @Test
  fun `converts debug log level to Sentry level`() {
    fixture = Fixture(minimumEventLevel = Level.DEBUG)
    fixture.logger.debug("testing debug level")

    verify(fixture.transport)
      .send(checkEvent { event -> assertEquals(SentryLevel.DEBUG, event.level) }, anyOrNull())
  }

  @Test
  fun `converts info log level to Sentry level`() {
    fixture = Fixture(minimumEventLevel = Level.INFO)
    fixture.logger.info("testing info level")

    verify(fixture.transport)
      .send(checkEvent { event -> assertEquals(SentryLevel.INFO, event.level) }, anyOrNull())
  }

  @Test
  fun `converts warn log level to Sentry level`() {
    fixture = Fixture(minimumEventLevel = Level.WARN)
    fixture.logger.warn("testing warn level")

    verify(fixture.transport)
      .send(checkEvent { event -> assertEquals(SentryLevel.WARNING, event.level) }, anyOrNull())
  }

  @Test
  fun `converts error log level to Sentry level`() {
    fixture = Fixture(minimumEventLevel = Level.ERROR)
    fixture.logger.error("testing error level")

    verify(fixture.transport)
      .send(checkEvent { event -> assertEquals(SentryLevel.ERROR, event.level) }, anyOrNull())
  }

  @Test
  fun `converts error log level to Sentry level with exception`() {
    fixture = Fixture(minimumEventLevel = Level.ERROR)
    fixture.logger.error("testing error level", RuntimeException("test exc"))

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
  fun `converts trace log level to Sentry log level`() {
    fixture = Fixture(minimumLevel = Level.TRACE, enableLogs = true)
    fixture.logger.trace("testing trace level")

    Sentry.flush(10)

    verify(fixture.transport)
      .send(checkLogs { logs -> assertEquals(SentryLogLevel.TRACE, logs.items.first().level) })
  }

  @Test
  fun `converts debug log level to Sentry log level`() {
    fixture = Fixture(minimumLevel = Level.DEBUG, enableLogs = true)
    fixture.logger.debug("testing debug level")

    Sentry.flush(10)

    verify(fixture.transport)
      .send(checkLogs { logs -> assertEquals(SentryLogLevel.DEBUG, logs.items.first().level) })
  }

  @Test
  fun `converts info log level to Sentry log level`() {
    fixture = Fixture(minimumLevel = Level.INFO, enableLogs = true)
    fixture.logger.info("testing info level")

    Sentry.flush(10)

    verify(fixture.transport)
      .send(checkLogs { logs -> assertEquals(SentryLogLevel.INFO, logs.items.first().level) })
  }

  @Test
  fun `converts warn log level to Sentry log level`() {
    fixture = Fixture(minimumLevel = Level.WARN, enableLogs = true)
    fixture.logger.warn("testing warn level")

    Sentry.flush(10)

    verify(fixture.transport)
      .send(checkLogs { logs -> assertEquals(SentryLogLevel.WARN, logs.items.first().level) })
  }

  @Test
  fun `converts error log level to Sentry log level`() {
    fixture = Fixture(minimumLevel = Level.ERROR, enableLogs = true)
    fixture.logger.error("testing error level")

    Sentry.flush(10)

    verify(fixture.transport)
      .send(checkLogs { logs -> assertEquals(SentryLogLevel.ERROR, logs.items.first().level) })
  }

  @Test
  fun `sends formatted log message if no encoder`() {
    fixture = Fixture(minimumLevel = Level.TRACE, enableLogs = true)
    fixture.logger.trace("Testing {} level", "TRACE")

    Sentry.flush(10)

    verify(fixture.transport)
      .send(
        checkLogs { logs ->
          val log = logs.items.first()
          assertEquals("Testing TRACE level", log.body)
          val attributes = log.attributes!!
          assertEquals("Testing {} level", attributes["sentry.message.template"]?.value)
          assertEquals("TRACE", attributes["sentry.message.parameter.0"]?.value)
          assertEquals("auto.log.logback", attributes["sentry.origin"]?.value)
        }
      )
  }

  @Test
  fun `does not send formatted log message if encoder is available but sendDefaultPii is off`() {
    var encoder = PatternLayoutEncoder()
    encoder.pattern = "encoderadded %msg"
    fixture = Fixture(minimumLevel = Level.TRACE, enableLogs = true, encoder = encoder)
    fixture.logger.trace("Testing {} level", "TRACE")

    Sentry.flush(10)

    verify(fixture.transport)
      .send(
        checkLogs { logs ->
          val log = logs.items.first()
          assertEquals("encoderadded Testing TRACE level", log.body)
          val attributes = log.attributes!!
          assertNull(attributes["sentry.message.template"])
          assertNull(attributes["sentry.message.parameter.0"])
        }
      )
  }

  @Test
  fun `sends formatted log message if encoder is available and sendDefaultPii is on but encoder throws`() {
    var encoder = ThrowingEncoder()
    fixture =
      Fixture(
        minimumLevel = Level.TRACE,
        enableLogs = true,
        sendDefaultPii = true,
        encoder = encoder,
      )
    fixture.logger.trace("Testing {} level", "TRACE")

    Sentry.flush(10)

    verify(fixture.transport)
      .send(
        checkLogs { logs ->
          val log = logs.items.first()
          assertEquals("Testing TRACE level", log.body)
          val attributes = log.attributes!!
          assertEquals("Testing {} level", attributes["sentry.message.template"]?.value)
          assertEquals("TRACE", attributes["sentry.message.parameter.0"]?.value)
        }
      )
  }

  @Test
  fun `sends formatted log message if encoder is available and sendDefaultPii is on`() {
    var encoder = PatternLayoutEncoder()
    encoder.pattern = "encoderadded %msg"
    fixture =
      Fixture(
        minimumLevel = Level.TRACE,
        enableLogs = true,
        sendDefaultPii = true,
        encoder = encoder,
      )
    fixture.logger.trace("Testing {} level", "TRACE")

    Sentry.flush(10)

    verify(fixture.transport)
      .send(
        checkLogs { logs ->
          val log = logs.items.first()
          assertEquals("encoderadded Testing TRACE level", log.body)
          val attributes = log.attributes!!
          assertEquals("Testing {} level", attributes["sentry.message.template"]?.value)
          assertEquals("TRACE", attributes["sentry.message.parameter.0"]?.value)
        }
      )
  }

  @Test
  fun `attaches thread information`() {
    fixture = Fixture(minimumEventLevel = Level.WARN)
    fixture.logger.warn("testing thread information")

    verify(fixture.transport)
      .send(checkEvent { event -> assertNotNull(event.getExtra("thread_name")) }, anyOrNull())
  }

  @Test
  fun `sets tags from MDC`() {
    fixture = Fixture(minimumEventLevel = Level.WARN)
    MDC.put("key", "value")
    fixture.logger.warn("testing MDC tags")

    verify(fixture.transport)
      .send(
        checkEvent { event -> assertEquals(mapOf("key" to "value"), event.contexts["MDC"]) },
        anyOrNull(),
      )
  }

  @Test
  fun `sets tags as sentry tags from MDC`() {
    fixture = Fixture(minimumEventLevel = Level.WARN, contextTags = listOf("contextTag1"))
    MDC.put("key", "value")
    MDC.put("contextTag1", "contextTag1Value")
    fixture.logger.warn("testing MDC tags")

    verify(fixture.transport)
      .send(
        checkEvent { event ->
          assertEquals(mapOf("key" to "value"), event.contexts["MDC"])
          assertEquals(mapOf("contextTag1" to "contextTag1Value"), event.tags)
        },
        anyOrNull(),
      )
  }

  @Test
  fun `ignore set tags with null values from MDC`() {
    fixture = Fixture(minimumEventLevel = Level.WARN)
    MDC.put("key1", null)
    MDC.put("key2", "value")
    fixture.logger.warn("testing MDC tags")

    verify(fixture.transport)
      .send(
        checkEvent { event ->
          assertNotNull(event.contexts["MDC"]) {
            val contextData = it as Map<*, *>
            assertNull(contextData["key1"])
            assertEquals("value", contextData["key2"])
          }
        },
        anyOrNull(),
      )
  }

  @Test
  fun `does not set MDC if all context entries are null`() {
    fixture = Fixture(minimumEventLevel = Level.WARN)
    MDC.put("key1", null)
    MDC.put("key2", null)
    fixture.logger.warn("testing MDC tags")

    verify(fixture.transport)
      .send(checkEvent { event -> assertNull(event.contexts["MDC"]) }, anyOrNull())
  }

  @Test
  fun `does not create MDC context when no MDC tags are set`() {
    fixture = Fixture(minimumEventLevel = Level.WARN)
    fixture.logger.warn("testing without MDC tags")

    verify(fixture.transport)
      .send(checkEvent { event -> assertFalse(event.contexts.containsKey("MDC")) }, anyOrNull())
  }

  @Test
  fun `attaches marker information`() {
    fixture = Fixture(minimumEventLevel = Level.WARN)
    val sqlMarker = MarkerFactory.getDetachedMarker("SQL")
    sqlMarker.add(MarkerFactory.getDetachedMarker("SQL_UPDATE"))
    sqlMarker.add(MarkerFactory.getDetachedMarker("SQL_QUERY"))

    fixture.logger.warn(sqlMarker, "testing marker tags")

    verify(fixture.transport)
      .send(
        checkEvent { event ->
          assertEquals("SQL [ SQL_UPDATE, SQL_QUERY ]", event.getExtra("marker"))
        },
        anyOrNull(),
      )
  }

  @Test
  fun `sets SDK version`() {
    fixture = Fixture(minimumEventLevel = Level.INFO)
    fixture.logger.info("testing sdk version")

    verify(fixture.transport)
      .send(
        checkEvent { event ->
          assertNotNull(event.sdk) {
            assertEquals(BuildConfig.SENTRY_LOGBACK_SDK_NAME, it.name)
            assertEquals(BuildConfig.VERSION_NAME, it.version)
            assertTrue(
              it.packageSet.any { pkg ->
                "maven:io.sentry:sentry-logback" == pkg.name &&
                  BuildConfig.VERSION_NAME == pkg.version
              }
            )
            assertTrue(it.integrationSet.contains("Logback"))
          }
        },
        anyOrNull(),
      )
  }

  @Test
  fun `attaches breadcrumbs with level higher than minimumBreadcrumbLevel`() {
    fixture = Fixture(minimumBreadcrumbLevel = Level.DEBUG, minimumEventLevel = Level.WARN)
    val utcTime = LocalDateTime.now(fixture.utcTimeZone)

    fixture.logger.debug("this should be a breadcrumb #1")
    fixture.logger.info("this should be a breadcrumb #2")
    fixture.logger.warn("testing message with breadcrumbs")

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
            assertEquals("io.sentry.logback.SentryAppenderTest", breadcrumb.category)
            assertEquals(SentryLevel.DEBUG, breadcrumb.level)
          }
        },
        anyOrNull(),
      )
  }

  @Test
  fun `does not attach breadcrumbs with level lower than minimumBreadcrumbLevel`() {
    fixture = Fixture(minimumBreadcrumbLevel = Level.INFO, minimumEventLevel = Level.WARN)

    fixture.logger.debug("this should NOT be a breadcrumb")
    fixture.logger.info("this should be a breadcrumb")
    fixture.logger.warn("testing message with breadcrumbs")

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
    fixture = Fixture()

    fixture.logger.debug(
      "this should not be a breadcrumb as the level is lower than the minimum INFO"
    )
    fixture.logger.info("this should be a breadcrumb")
    fixture.logger.warn("this should not be sent as the event but be a breadcrumb")
    fixture.logger.error("this should be sent as the event")

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
    fixture = Fixture()
    fixture.logger.error("some event")

    verify(fixture.transport)
      .send(
        checkEvent { event -> assertEquals("release from sentry.properties", event.release) },
        anyOrNull(),
      )
  }

  @Test
  fun `does not initialize Sentry when environment variable with DSN is passed through environment variable that is not set`() {
    // environment variables referenced in the logback.xml that are not set in the OS, have value
    // "ENV_NAME_IS_UNDEFINED"
    fixture = Fixture(dsn = "DSN_IS_UNDEFINED", minimumEventLevel = Level.DEBUG)

    assertTrue(
      fixture.loggerContext.statusManager.copyOfStatusList.none { it.level == Status.WARN }
    )
  }

  @Test
  fun `does initialize Sentry when DSN is not set`() {
    System.setProperty("sentry.dsn", "http://key@localhost/proj")
    fixture = Fixture(dsn = null, minimumEventLevel = Level.DEBUG)

    assertTrue(Sentry.isEnabled())
    System.clearProperty("sentry.dsn")
  }

  @Test
  fun `does not crash on ThrowableProxyVO`() {
    fixture = Fixture()
    val throwableProxy = ThrowableProxy(RuntimeException("hello proxy throwable"))
    val loggingEvent = LoggingEvent()
    loggingEvent.level = Level.ERROR
    loggingEvent.setThrowableProxy(throwableProxy)
    val loggingEventVO = LoggingEventVO.build(loggingEvent)

    fixture.appender.append(loggingEventVO)

    verify(fixture.transport)
      .send(
        checkEvent { event ->
          assertEquals(SentryLevel.ERROR, event.level)
          assertNull(event.exceptions)
        },
        anyOrNull(),
      )
  }
}
