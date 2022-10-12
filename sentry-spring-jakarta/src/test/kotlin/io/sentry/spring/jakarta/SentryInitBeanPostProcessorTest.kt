package io.sentry.spring.jakarta

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.sentry.IHub
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import kotlin.test.Test

class SentryInitBeanPostProcessorTest {

    @Test
    fun closesSentryOnApplicationContextDestroy() {
        val ctx = AnnotationConfigApplicationContext(TestConfig::class.java)
        val hub = ctx.getBean(IHub::class.java)
        ctx.close()
        verify(hub).close()
    }

    @Configuration
    open class TestConfig {

        @Bean(destroyMethod = "")
        open fun hub() = mock<IHub>()

        @Bean
        open fun sentryInitBeanPostProcessor() = SentryInitBeanPostProcessor(hub())
    }
}
