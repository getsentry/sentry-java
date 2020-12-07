package io.sentry.spring.boot.datasource.p6spy

import io.sentry.p6spy.SentryJdbcEventListener
import io.sentry.spring.boot.SentryAutoConfiguration
import javax.sql.DataSource
import kotlin.test.Test
import org.assertj.core.api.Assertions
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class SentryP6SpyAutoConfigurationTest {
    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(SentryAutoConfiguration::class.java, SentryP6SpyAutoConfiguration::class.java))

    @Test
    fun `when Datasource is not on the classpath, does not create p6spy beans`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj", "sentry.enable-tracing=true")
            .withClassLoader(FilteredClassLoader(DataSource::class.java))
            .run {
                Assertions.assertThat(it).doesNotHaveBean(SentryJdbcEventListener::class.java)
            }
    }

    @Test
    fun `when SentryJdbcEventListener is not on the classpath, does not create datasource-proxy beans`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj", "sentry.enable-tracing=true")
            .withClassLoader(FilteredClassLoader(SentryJdbcEventListener::class.java))
            .run {
                Assertions.assertThat(it).doesNotHaveBean(SentryJdbcEventListener::class.java)
            }
    }

    @Test
    fun `when Datasource and SentryJdbcEventListener are on the classpath, creates datasource-proxy beans`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj", "sentry.enable-tracing=true")
            .run {
                Assertions.assertThat(it).hasSingleBean(SentryJdbcEventListener::class.java)
            }
    }
}
