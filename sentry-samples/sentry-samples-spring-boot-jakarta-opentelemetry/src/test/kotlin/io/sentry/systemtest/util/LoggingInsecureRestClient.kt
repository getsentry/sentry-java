package io.sentry.systemtest.util

import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.client.BufferingClientHttpRequestFactory
import org.springframework.web.client.RestTemplate
import java.time.Duration

open class LoggingInsecureRestClient {

    protected fun restTemplate(): RestTemplate {
        return RestTemplateBuilder().also {
            it.readTimeout(Duration.ofMillis(20000))
            it.connectTimeout(Duration.ofMillis(20000))
        }.build().also {
            it.requestFactory = BufferingClientHttpRequestFactory(it.requestFactory)
        }
    }
}
