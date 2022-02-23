package io.sentry.spring.boot

import io.sentry.jdbc.SentryJdbcEventListener
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import kotlin.test.BeforeTest
import kotlin.test.Test

class SentryP6SpyAutoConfigurationTest {

    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(SentryP6SpyAutoConfiguration::class.java, SentryAutoConfiguration::class.java))

    @BeforeTest
    fun `reset system property`() {
        System.clearProperty("p6spy.config.modulelist")
    }

    @Test
    fun `sets noop appender when logs are disabled`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj", "sentry.jdbc.disable-log-file=true")
            .run {
                assertThat(System.getProperty("p6spy.config.modulelist")).isEqualTo("com.p6spy.engine.spy.P6SpyFactory")
            }
    }

    @Test
    fun `when logs are enabled does not overwrite p6spy system property`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj", "sentry.jdbc.disable-log-file=false")
            .run {
                assertThat(System.getProperty("p6spy.config.modulelist")).isNull()
            }
    }

    @Test
    fun `when logs are defined does not overwrite p6spy system property`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj", "sentry.jdbc.disable-log-file=false")
            .run {
                assertThat(System.getProperty("p6spy.config.modulelist")).isNull()
            }
    }

    @Test
    fun `when sentry-jdbc is not on the classpath, P6SpyLogsDisabler is not configured`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj", "sentry.jdbc.disable-log-file=true")
            .withClassLoader(FilteredClassLoader(SentryJdbcEventListener::class.java))
            .run { ctx ->
                assertThatThrownBy { ctx.getBean(SentryP6SpyAutoConfiguration.P6SpyLogsDisabler::class.java) }
                    .isInstanceOf(NoSuchBeanDefinitionException::class.java)
            }
    }
}
