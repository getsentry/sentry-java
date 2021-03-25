package io.sentry.spring.boot.jdbc

import io.sentry.dsproxy.SentryQueryExecutionListener
import io.sentry.spring.boot.SentryAutoConfiguration
import javax.sql.DataSource
import kotlin.test.Test
import org.assertj.core.api.Assertions
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class SentryDsProxyAutoConfigurationTest {
    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(SentryAutoConfiguration::class.java, SentryDsProxyAutoConfiguration::class.java))

    @Test
    fun `when Datasource is not on the classpath, does not create datasource-proxy beans`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj", "sentry.enable-tracing=true")
            .withClassLoader(FilteredClassLoader(DataSource::class.java))
            .run {
                Assertions.assertThat(it).doesNotHaveBean(SentryQueryExecutionListener::class.java)
            }
    }

    @Test
    fun `when SentryQueryExecutionListener is not on the classpath, does not create datasource-proxy beans`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj", "sentry.enable-tracing=true")
            .withClassLoader(FilteredClassLoader(SentryQueryExecutionListener::class.java))
            .run {
                Assertions.assertThat(it).doesNotHaveBean(SentryQueryExecutionListener::class.java)
            }
    }

    @Test
    fun `when Datasource and SentryQueryExecutionListener are on the classpath, creates datasource-proxy beans`() {
        contextRunner.withPropertyValues("sentry.dsn=http://key@localhost/proj", "sentry.enable-tracing=true")
            .run {
                Assertions.assertThat(it).hasSingleBean(SentryQueryExecutionListener::class.java)
            }
    }
}
