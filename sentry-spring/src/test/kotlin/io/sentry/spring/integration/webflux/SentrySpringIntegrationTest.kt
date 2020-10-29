package io.sentry.spring.integration.webflux

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.reset
import com.nhaarman.mockitokotlin2.verify
import io.sentry.IHub
import io.sentry.spring.EnableSentry
import io.sentry.spring.reactive.SentryReactiveWebHelper
import io.sentry.test.checkEvent
import io.sentry.transport.ITransport
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService
import org.springframework.security.core.userdetails.User
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RunWith(SpringRunner::class)
@SpringBootTest(
    properties = ["spring.main.web-application-type=REACTIVE"],
    classes = [App::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class SentrySpringIntegrationTest {

    @Autowired
    lateinit var transport: ITransport

    @LocalServerPort
    lateinit var port: Integer

    @Before
    fun `reset mocks`() {
        reset(transport)
    }

    @Test
    fun `attaches request and user information to SentryEvents`() {
        val restTemplate = TestRestTemplate().withBasicAuth("user", "password")
        val headers = HttpHeaders()
        headers["X-FORWARDED-FOR"] = listOf("169.128.0.1")
        val entity = HttpEntity<Void>(headers)

        restTemplate.exchange("http://localhost:$port/hello", HttpMethod.GET, entity, Void::class.java)

        await.untilAsserted {
            verify(transport).send(checkEvent { event ->
                assertThat(event.request).isNotNull()
                assertThat(event.request.url).isEqualTo("http://localhost:$port/hello")
                assertThat(event.user).isNotNull()
                assertThat(event.user.username).isEqualTo("user")
                assertThat(event.user.ipAddress).isEqualTo("169.128.0.1")
            })
        }
    }

    @Test
    fun `attaches first ip address if multiple addresses exist in a header`() {
        val restTemplate = TestRestTemplate().withBasicAuth("user", "password")
        val headers = HttpHeaders()
        headers["X-FORWARDED-FOR"] = listOf("169.128.0.1, 192.168.0.1")
        val entity = HttpEntity<Void>(headers)

        restTemplate.exchange("http://localhost:$port/hello", HttpMethod.GET, entity, Void::class.java)

        await.untilAsserted {
            verify(transport).send(checkEvent { event ->
                assertThat(event.user.ipAddress).isEqualTo("169.128.0.1")
            })
        }
    }

    @Test
    fun `sends events for unhandled exceptions`() {
        val restTemplate = TestRestTemplate().withBasicAuth("user", "password")

        restTemplate.getForEntity("http://localhost:$port/throws", String::class.java)

        await.untilAsserted {
            verify(transport).send(checkEvent { event ->
                assertThat(event.exceptions).isNotEmpty
                val ex = event.exceptions[1]
                assertThat(ex.value).isEqualTo("something went wrong")
                assertThat(ex.mechanism.isHandled).isFalse()
            })
        }
    }
}

@SpringBootApplication
@EnableSentry(dsn = "http://key@localhost/proj", sendDefaultPii = true)
open class App {

    @Bean
    open fun mockTransport() = mock<ITransport>()
}

@RestController
class HelloController {

    @GetMapping("/hello")
    fun hello(): Mono<String> {
        return SentryReactiveWebHelper.captureWithRequestHub { hub: IHub -> hub.captureMessage("hello") }
            .thenReturn("Hello")
    }

    @GetMapping("/throws")
    fun throws(): Mono<String> {
        throw RuntimeException("something went wrong")
    }
}

@Configuration
open class SecurityConfiguration {

    @Bean
    open fun securityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain? {
        return http.csrf()
            .disable()
            .authorizeExchange()
            .anyExchange()
            .authenticated()
            .and()
            .httpBasic()
            .and()
            .build()
    }

    @Bean
    open fun userDetailsService(): MapReactiveUserDetailsService? {
        val encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()
        val user = User.builder()
            .passwordEncoder { rawPassword: String? -> encoder.encode(rawPassword) }
            .username("user")
            .password("password")
            .roles("USER")
            .build()
        return MapReactiveUserDetailsService(user)
    }
}
