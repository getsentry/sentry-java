package io.sentry.systemtest.util

import org.springframework.http.client.BufferingClientHttpRequestFactory
import org.springframework.web.client.RestTemplate

open class LoggingInsecureRestClient {

    protected fun restTemplate(): RestTemplate {
        return RestTemplate().also {
            it.requestFactory = BufferingClientHttpRequestFactory(it.requestFactory)
        }
    }
}
