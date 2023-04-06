package io.sentry.samples.spring.boot

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.client.RestTemplate
import org.springframework.web.reactive.function.client.WebClient

@SpringBootApplication
@EnableScheduling
class SentryDemoApplication {

    @Bean
    fun restTemplate(builder: RestTemplateBuilder): RestTemplate {
        return builder.build()
    }

    @Bean
    fun webClient(builder: WebClient.Builder): WebClient {
        return builder.build()
    }
}

fun main(args: Array<String>) {
    runApplication<SentryDemoApplication>(*args)
}
