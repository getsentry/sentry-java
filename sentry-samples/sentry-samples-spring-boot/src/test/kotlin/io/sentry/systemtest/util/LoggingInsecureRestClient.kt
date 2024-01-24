package io.sentry.systemtest.util

import org.apache.http.impl.client.HttpClients
import org.springframework.http.client.BufferingClientHttpRequestFactory
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.web.client.RestTemplate

open class LoggingInsecureRestClient {

    protected fun restTemplate(): RestTemplate {
        val requestFactory = BufferingClientHttpRequestFactory(
            HttpComponentsClientHttpRequestFactory(HttpClients.createDefault())
        )
        return RestTemplate(requestFactory).also {
            it.messageConverters.add(0, jacksonConverter())
        }
    }

    private fun jacksonConverter(): org.springframework.http.converter.json.MappingJackson2HttpMessageConverter {
        val converter = org.springframework.http.converter.json.MappingJackson2HttpMessageConverter()
        converter.objectMapper = objectMapper()
        return converter
    }

    private fun objectMapper(): com.fasterxml.jackson.databind.ObjectMapper {
        val builder = org.springframework.http.converter.json.Jackson2ObjectMapperBuilder.json()
        val objectMapper: com.fasterxml.jackson.databind.ObjectMapper = builder.createXmlMapper(false).build()
        objectMapper.registerModule(com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
        objectMapper.configure(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        return objectMapper
    }
}
