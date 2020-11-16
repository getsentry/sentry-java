package io.sentry.spring

import io.sentry.IHub
import io.sentry.SentryOptions
import kotlin.test.Test
import org.assertj.core.api.Assertions.assertThat
import org.springframework.boot.context.annotation.UserConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class EnableSentryTest {
    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(UserConfigurations.of(AppConfig::class.java))

    @Test
    fun `sets properties from environment on SentryOptions`() {
        ApplicationContextRunner()
            .withConfiguration(UserConfigurations.of(AppConfigWithDefaultSendPii::class.java))
            .run {
                assertThat(it).hasSingleBean(SentryOptions::class.java)
                val options = it.getBean(SentryOptions::class.java)
                assertThat(options.dsn).isEqualTo("http://key@localhost/proj")
                assertThat(options.isSendDefaultPii).isTrue()
            }

        ApplicationContextRunner()
            .withConfiguration(UserConfigurations.of(AppConfigWithEmptyDsn::class.java))
            .run {
                assertThat(it).hasSingleBean(SentryOptions::class.java)
                val options = it.getBean(SentryOptions::class.java)
                assertThat(options.dsn).isEmpty()
                assertThat(options.isSendDefaultPii).isFalse()
            }
    }

    @Test
    fun `sets client name and SDK version`() {
        contextRunner.run {
            assertThat(it).hasSingleBean(SentryOptions::class.java)
            val options = it.getBean(SentryOptions::class.java)
            assertThat(options.sentryClientName).isEqualTo("sentry.java.spring")
            assertThat(options.sdkVersion).isNotNull
            assertThat(options.sdkVersion!!.name).isEqualTo("sentry.java.spring")
            assertThat(options.sdkVersion!!.version).isEqualTo(BuildConfig.VERSION_NAME)
            assertThat(options.sdkVersion!!.packages).isNotNull
            assertThat(options.sdkVersion!!.packages!!.map { pkg -> pkg.name }).contains("maven:sentry-spring")
        }
    }

    @Test
    fun `enables external configuration on SentryOptions`() {
        contextRunner.run {
            assertThat(it).hasSingleBean(SentryOptions::class.java)
            val options = it.getBean(SentryOptions::class.java)
            assertThat(options.isEnableExternalConfiguration).isTrue()
        }
    }

    @Test
    fun `creates Sentry Hub`() {
        contextRunner.run {
            assertThat(it).hasSingleBean(IHub::class.java)
        }
    }

    @Test
    fun `creates SentrySpringRequestListener`() {
        contextRunner.run {
            assertThat(it).hasSingleBean(SentrySpringRequestListener::class.java)
        }
    }

    @Test
    fun `creates SentryExceptionResolver`() {
        contextRunner.run {
            assertThat(it).hasSingleBean(SentryExceptionResolver::class.java)
            assertThat(it).getBean(SentryExceptionResolver::class.java)
                .hasFieldOrPropertyWithValue("order", Integer.MIN_VALUE)
        }
    }

    @Test
    fun `creates SentryExceptionResolver with order set in the @EnableSentry annotation`() {
        ApplicationContextRunner().withConfiguration(UserConfigurations.of(AppConfigWithExceptionResolverOrderIntegerMaxValue::class.java))
            .run {
                assertThat(it).hasSingleBean(SentryExceptionResolver::class.java)
                assertThat(it).getBean(SentryExceptionResolver::class.java)
                    .hasFieldOrPropertyWithValue("order", Integer.MAX_VALUE)
            }
    }

    @EnableSentry(dsn = "http://key@localhost/proj")
    class AppConfig

    @EnableSentry(dsn = "")
    class AppConfigWithEmptyDsn

    @EnableSentry(dsn = "http://key@localhost/proj", sendDefaultPii = true)
    class AppConfigWithDefaultSendPii

    @EnableSentry(dsn = "http://key@localhost/proj", exceptionResolverOrder = Integer.MAX_VALUE)
    class AppConfigWithExceptionResolverOrderIntegerMaxValue
}
