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
    fun `creates Sentry Hub`() {
        contextRunner.run {
            assertThat(it).hasSingleBean(IHub::class.java)
        }
    }

    @Test
    fun `creates SentryRequestFilter`() {
        contextRunner.run {
            assertThat(it).hasSingleBean(SentryRequestFilter::class.java)
        }
    }

    @Test
    fun `creates SentryExceptionResolver`() {
        contextRunner.run {
            assertThat(it).hasSingleBean(SentryExceptionResolver::class.java)
        }
    }

    @EnableSentry(dsn = "http://key@localhost/proj")
    class AppConfig

    @EnableSentry(dsn = "")
    class AppConfigWithEmptyDsn

    @EnableSentry(dsn = "http://key@localhost/proj", sendDefaultPii = true)
    class AppConfigWithDefaultSendPii
}
