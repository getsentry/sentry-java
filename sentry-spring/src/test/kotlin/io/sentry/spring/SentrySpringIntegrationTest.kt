package io.sentry.spring

import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.reset
import com.nhaarman.mockitokotlin2.verify
import io.sentry.core.IHub
import io.sentry.core.Sentry
import io.sentry.core.SentryEvent
import io.sentry.core.SentryOptions
import io.sentry.core.exception.ExceptionMechanismException
import io.sentry.core.transport.ITransport
import java.lang.RuntimeException
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
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RunWith(SpringRunner::class)
@SpringBootTest(
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
            verify(transport).send(check { event: SentryEvent ->
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
            verify(transport).send(check { event: SentryEvent ->
                assertThat(event.user.ipAddress).isEqualTo("169.128.0.1")
            })
        }
    }

    @Test
    fun `sends events for unhandled exceptions`() {
        val restTemplate = TestRestTemplate().withBasicAuth("user", "password")

        restTemplate.getForEntity("http://localhost:$port/throws", String::class.java)

        await.untilAsserted {
            verify(transport).send(check { event: SentryEvent ->
                assertThat(event.throwable).isNotNull()
                assertThat(event.throwable).isInstanceOf(ExceptionMechanismException::class.java)
                val ex = event.throwable as ExceptionMechanismException
                assertThat(ex.throwable.message).isEqualTo("something went wrong")
                assertThat(ex.exceptionMechanism.isHandled).isFalse()
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
    fun hello() {
        Sentry.captureMessage("hello")
    }

    @GetMapping("/throws")
    fun throws() {
        throw RuntimeException("something went wrong")
    }
}

@Configuration
open class SecurityConfiguration(
    private val hub: IHub,
    private val options: SentryOptions
) : WebSecurityConfigurerAdapter() {

    override fun configure(http: HttpSecurity) {
        http
            .addFilterAfter(SentrySecurityFilter(hub, options), AnonymousAuthenticationFilter::class.java)
            .csrf().disable()
            .authorizeRequests().anyRequest().authenticated()
            .and()
            .httpBasic()
    }

    @Bean
    override fun userDetailsService(): UserDetailsService {
        val encoder: PasswordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()
        val user: UserDetails = User
            .builder()
            .passwordEncoder { rawPassword -> encoder.encode(rawPassword) }
            .username("user")
            .password("password")
            .roles("USER")
            .build()
        return InMemoryUserDetailsManager(user)
    }
}
