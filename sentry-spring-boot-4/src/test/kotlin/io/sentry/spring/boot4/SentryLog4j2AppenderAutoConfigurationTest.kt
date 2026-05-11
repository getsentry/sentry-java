package io.sentry.spring.boot4

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.sentry.ITransportFactory
import io.sentry.NoOpTransportFactory
import io.sentry.ScopesAdapter
import io.sentry.log4j2.SentryAppender
import kotlin.test.BeforeTest
import kotlin.test.Test
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.Appender
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.config.DefaultConfiguration
import org.apache.logging.log4j.core.config.LoggerConfig
import org.assertj.core.api.Assertions.assertThat
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

class SentryLog4j2AppenderAutoConfigurationTest {

  private val baseContextRunner =
    ApplicationContextRunner()
      .withConfiguration(
        AutoConfigurations.of(
          SentryLog4j2AppenderAutoConfiguration::class.java,
          SentryAutoConfiguration::class.java,
        )
      )
      .withPropertyValues(
        "sentry.shutdownTimeoutMillis=0",
        "sentry.sessionFlushTimeoutMillis=0",
        "sentry.flushTimeoutMillis=0",
        "sentry.readTimeoutMillis=50",
        "sentry.connectionTimeoutMillis=50",
        "sentry.send-modules=false",
        "sentry.attach-stacktrace=false",
        "sentry.attach-threads=false",
        "sentry.enable-backpressure-handling=false",
        "sentry.enable-spotlight=false",
        "sentry.debug=false",
        "sentry.max-breadcrumbs=0",
      )

  private val contextRunner =
    baseContextRunner
      .withLog4j2CoreProvider()
      .withUserConfiguration(NoOpTransportConfiguration::class.java)

  private val dsnEnabledRunner =
    baseContextRunner
      .withLog4j2CoreProvider()
      .withPropertyValues("sentry.dsn=http://key@localhost/proj")
      .withUserConfiguration(NoOpTransportConfiguration::class.java)

  private val log4j2BridgeDsnEnabledRunner =
    baseContextRunner
      .withClassLoader(FilteredClassLoader("org.apache.logging.log4j.core.impl.Log4jProvider"))
      .withPropertyValues("sentry.dsn=http://key@localhost/proj")
      .withUserConfiguration(NoOpTransportConfiguration::class.java)

  private val loggerContext: LoggerContext
    get() = LogManager.getContext(false) as LoggerContext

  private val configuration
    get() = loggerContext.configuration

  private val rootLogger
    get() = configuration.rootLogger

  @BeforeTest
  fun `reset Log4j2 context`() {
    useLog4j2Core()
    resetLog4j2Context()
  }

  @Test
  fun `does not configure SentryAppender when auto-configuration dsn is not set`() {
    contextRunner.run { assertThat(rootLogger.getAppenders(SentryAppender::class.java)).isEmpty() }
  }

  @Test
  fun `configures SentryAppender`() {
    dsnEnabledRunner.run {
      assertThat(rootLogger.getAppenders(SentryAppender::class.java)).hasSize(1)
    }
  }

  @Test
  fun `configures SentryAppender for configured loggers`() {
    dsnEnabledRunner
      .withPropertyValues("sentry.logging.loggers[0]=foo.bar", "sentry.logging.loggers[1]=baz")
      .run {
        val fooBarLogger = configuration.getLoggerConfig("foo.bar")
        val bazLogger = configuration.getLoggerConfig("baz")

        assertThat(rootLogger.getAppenders(SentryAppender::class.java)).hasSize(0)
        assertThat(fooBarLogger.getAppenders(SentryAppender::class.java)).hasSize(1)
        assertThat(bazLogger.getAppenders(SentryAppender::class.java)).hasSize(1)
      }
  }

  @Test
  fun `does not configure SentryAppender for descendant logger covered by ancestor logger`() {
    dsnEnabledRunner
      .withPropertyValues("sentry.logging.loggers[0]=ROOT", "sentry.logging.loggers[1]=com.example")
      .run {
        assertThat(rootLogger.getAppenders(SentryAppender::class.java)).hasSize(1)
        assertThat(configuration.loggers).doesNotContainKey("com.example")
      }
  }

  @Test
  fun `configures SentryAppender for descendant logger with additivity disabled`() {
    val loggerConfig = LoggerConfig("com.example", null, false)
    configuration.addLogger("com.example", loggerConfig)
    loggerContext.updateLoggers(configuration)

    dsnEnabledRunner
      .withPropertyValues("sentry.logging.loggers[0]=ROOT", "sentry.logging.loggers[1]=com.example")
      .run {
        assertThat(rootLogger.getAppenders(SentryAppender::class.java)).hasSize(1)
        assertThat(
            configuration.getLoggerConfig("com.example").getAppenders(SentryAppender::class.java)
          )
          .hasSize(1)
      }
  }

  @Test
  fun `configures SentryAppender for none of the loggers if so configured`() {
    dsnEnabledRunner.withPropertyValues("sentry.logging.loggers=").run {
      val fooBarLogger = configuration.getLoggerConfig("foo.bar")
      val bazLogger = configuration.getLoggerConfig("baz")

      assertThat(rootLogger.getAppenders(SentryAppender::class.java)).hasSize(0)
      assertThat(fooBarLogger.getAppenders(SentryAppender::class.java)).hasSize(0)
      assertThat(bazLogger.getAppenders(SentryAppender::class.java)).hasSize(0)
    }
  }

  @Test
  fun `does not overwrite Spring Boot Sentry options`() {
    dsnEnabledRunner.withPropertyValues("sentry.environment=boot-env").run {
      assertThat(rootLogger.getAppenders(SentryAppender::class.java)).hasSize(1)
      assertThat(ScopesAdapter.getInstance().options.environment).isEqualTo("boot-env")
    }
  }

  @Test
  fun `sets SentryAppender properties`() {
    dsnEnabledRunner
      .withPropertyValues(
        "sentry.logging.minimum-event-level=info",
        "sentry.logging.minimum-breadcrumb-level=debug",
        "sentry.logging.minimum-level=error",
      )
      .run {
        val appenders = rootLogger.getAppenders(SentryAppender::class.java)
        assertThat(appenders).hasSize(1)
        val sentryAppender = appenders[0] as SentryAppender

        assertThat(sentryAppender.getLevel("minimumBreadcrumbLevel")).isEqualTo(Level.DEBUG)
        assertThat(sentryAppender.getLevel("minimumEventLevel")).isEqualTo(Level.INFO)
        assertThat(sentryAppender.getLevel("minimumLevel")).isEqualTo(Level.ERROR)
      }
  }

  @Test
  fun `does not configure SentryAppender when logging is disabled`() {
    contextRunner.withPropertyValues("sentry.logging.enabled=false").run {
      assertThat(rootLogger.getAppenders(SentryAppender::class.java)).isEmpty()
    }
  }

  @Test
  fun `does not configure SentryAppender when appender is already configured`() {
    val sentryAppender =
      SentryAppender(
        "customAppender",
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        io.sentry.ScopesAdapter.getInstance(),
        null,
      )
    sentryAppender.start()
    configuration.addAppender(sentryAppender)
    rootLogger.addAppender(sentryAppender, null, null)
    loggerContext.updateLoggers()

    dsnEnabledRunner.run {
      val appenders = rootLogger.getAppenders(SentryAppender::class.java)
      assertThat(appenders).hasSize(1)
      assertThat(appenders.first().name).isEqualTo("customAppender")
    }
  }

  @Test
  fun `does not configure SentryAppender when active Log4j2 context is not Log4j2 Core`() {
    val logbackLogger = LoggerFactory.getLogger(SentryLog4j2Initializer::class.java) as Logger
    val listAppender = ListAppender<ILoggingEvent>()
    listAppender.start()
    logbackLogger.addAppender(listAppender)

    try {
      useSlf4jBridge()
      log4j2BridgeDsnEnabledRunner.run {
        assertThat(listAppender.list.map { it.formattedMessage }).anyMatch {
          it.contains("Sentry Log4j2 appender was not configured")
        }
      }
    } finally {
      logbackLogger.detachAppender(listAppender)
      listAppender.stop()
    }
  }

  @Test
  fun `does not configure SentryAppender when log4j2 is not on the classpath`() {
    baseContextRunner
      .withPropertyValues("sentry.dsn=http://key@localhost/proj")
      .withClassLoader(FilteredClassLoader(LoggerContext::class.java))
      .run { assertThat(rootLogger.getAppenders(SentryAppender::class.java)).isEmpty() }
  }

  @Test
  fun `does not configure SentryAppender when sentry-log4j2 module is not on the classpath`() {
    baseContextRunner
      .withPropertyValues("sentry.dsn=http://key@localhost/proj")
      .withClassLoader(FilteredClassLoader(SentryAppender::class.java))
      .run { assertThat(rootLogger.getAppenders(SentryAppender::class.java)).isEmpty() }
  }

  @Configuration(proxyBeanMethods = false)
  open class NoOpTransportConfiguration {

    @Bean
    open fun noOpTransportFactory(): ITransportFactory {
      return NoOpTransportFactory.getInstance()
    }
  }
}

fun <T> org.apache.logging.log4j.core.config.LoggerConfig.getAppenders(
  clazz: Class<T>
): List<Appender> {
  return this.appenders.values.filter { it.javaClass == clazz }
}

private fun ApplicationContextRunner.withLog4j2CoreProvider(): ApplicationContextRunner =
  withClassLoader(FilteredClassLoader("org.apache.logging.slf4j"))

private fun useLog4j2Core() {
  LogManager.setFactory(org.apache.logging.log4j.core.impl.Log4jContextFactory())
}

private fun useSlf4jBridge() {
  val factory =
    Class.forName("org.apache.logging.slf4j.SLF4JLoggerContextFactory")
      .getDeclaredConstructor()
      .newInstance() as org.apache.logging.log4j.spi.LoggerContextFactory
  LogManager.setFactory(factory)
}

private fun resetLog4j2Context() {
  val loggerContext = LogManager.getContext(false) as? LoggerContext ?: return
  loggerContext.reconfigure(DefaultConfiguration())
}

private fun SentryAppender.getLevel(fieldName: String): Level {
  val field = SentryAppender::class.java.getDeclaredField(fieldName)
  field.isAccessible = true
  return field.get(this) as Level
}
