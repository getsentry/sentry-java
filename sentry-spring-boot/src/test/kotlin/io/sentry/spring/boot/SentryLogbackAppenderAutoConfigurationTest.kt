package io.sentry.spring.boot

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.Appender
import io.sentry.logback.SentryAppender
import org.assertj.core.api.Assertions.assertThat
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import kotlin.test.BeforeTest
import kotlin.test.Test

class SentryLogbackAppenderAutoConfigurationTest {

    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(SentryLogbackAppenderAutoConfiguration::class.java, SentryAutoConfiguration::class.java))

    private val rootLogger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger

    @BeforeTest
    fun `reset Logback context`() {
        val loggerContext = LoggerFactory.getILoggerFactory() as LoggerContext
        loggerContext.reset()
    }

    @Test
    fun `does not configure SentryAppender when auto-configuration dsn is not set`() {
        contextRunner
            .run {
                assertThat(rootLogger.getAppenders(SentryAppender::class.java)).isEmpty()
            }
    }

    @Test
    fun `configures SentryAppender`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .run {
                assertThat(rootLogger.getAppenders(SentryAppender::class.java)).hasSize(1)
            }
    }

    @Test
    fun `configures SentryAppender for configured loggers`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj", "sentry.logging.loggers[0]=foo.bar", "sentry.logging.loggers[1]=baz")
            .run {
                val fooBarLogger = LoggerFactory.getLogger("foo.bar") as Logger
                val bazLogger = LoggerFactory.getLogger("baz") as Logger

                assertThat(rootLogger.getAppenders(SentryAppender::class.java)).hasSize(0)
                assertThat(fooBarLogger.getAppenders(SentryAppender::class.java)).hasSize(1)
                assertThat(bazLogger.getAppenders(SentryAppender::class.java)).hasSize(1)
            }
    }

    @Test
    fun `configures SentryAppender for none of the loggers if so configured`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj", "sentry.logging.loggers=")
            .run {
                val fooBarLogger = LoggerFactory.getLogger("foo.bar") as Logger
                val bazLogger = LoggerFactory.getLogger("baz") as Logger

                assertThat(rootLogger.getAppenders(SentryAppender::class.java)).hasSize(0)
                assertThat(fooBarLogger.getAppenders(SentryAppender::class.java)).hasSize(0)
                assertThat(bazLogger.getAppenders(SentryAppender::class.java)).hasSize(0)
            }
    }

    @Test
    fun `sets SentryAppender properties`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj", "sentry.logging.minimum-event-level=info", "sentry.logging.minimum-breadcrumb-level=debug", "sentry.logging.minimum-level=error")
            .run {
                val appenders = rootLogger.getAppenders(SentryAppender::class.java)
                assertThat(appenders).hasSize(1)
                val sentryAppender = appenders[0] as SentryAppender

                assertThat(sentryAppender.minimumBreadcrumbLevel).isEqualTo(Level.DEBUG)
                assertThat(sentryAppender.minimumEventLevel).isEqualTo(Level.INFO)
                assertThat(sentryAppender.minimumLevel).isEqualTo(Level.ERROR)
            }
    }

    @Test
    fun `does not configure SentryAppender when logging is disabled`() {
        contextRunner.withPropertyValues("sentry.logging.enabled=false")
            .run {
                assertThat(rootLogger.getAppenders(SentryAppender::class.java)).isEmpty()
            }
    }

    @Test
    fun `does not configure SentryAppender when appender is already configured`() {
        val loggerContext = LoggerFactory.getILoggerFactory() as LoggerContext
        val sentryAppender = SentryAppender()
        sentryAppender.name = "customAppender"
        sentryAppender.context = loggerContext
        sentryAppender.start()
        rootLogger.addAppender(sentryAppender)

        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .run {
                val appenders = rootLogger.getAppenders(SentryAppender::class.java)
                assertThat(appenders).hasSize(1)
                assertThat(appenders.first().name).isEqualTo("customAppender")
            }
    }

    @Test
    fun `does not configure SentryAppender when logback is not on the classpath`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .withClassLoader(FilteredClassLoader(LoggerContext::class.java))
            .run {
                assertThat(rootLogger.getAppenders(SentryAppender::class.java)).isEmpty()
            }
    }

    @Test
    fun `does not configure SentryAppender when sentry-logback module is not on the classpath`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj")
            .withClassLoader(FilteredClassLoader(SentryAppender::class.java))
            .run {
                assertThat(rootLogger.getAppenders(SentryAppender::class.java)).isEmpty()
            }
    }
}

fun <T> Logger.getAppenders(clazz: Class<T>): List<Appender<ILoggingEvent>> {
    return this.iteratorForAppenders().asSequence().toList()
        .filter { it.javaClass == clazz }
}
