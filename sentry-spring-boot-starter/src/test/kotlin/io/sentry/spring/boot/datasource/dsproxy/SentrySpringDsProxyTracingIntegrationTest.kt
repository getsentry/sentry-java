package io.sentry.spring.boot.datasource.dsproxy

import com.nhaarman.mockitokotlin2.atLeastOnce
import com.nhaarman.mockitokotlin2.verify
import io.sentry.spring.boot.datasource.p6spy.SentryP6SpyAutoConfiguration
import io.sentry.test.checkTransaction
import io.sentry.transport.ITransport
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RunWith(SpringRunner::class)
@SpringBootTest(
    classes = [TracingApp::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["sentry.dsn=http://key@localhost/proj", "sentry.enable-tracing=true", "sentry.traces-sample-rate=1.0"]
)
class SentrySpringDsProxyTracingIntegrationTest {

    @MockBean
    lateinit var transport: ITransport

    @LocalServerPort
    lateinit var port: Integer

    @Test
    fun `attaches span from database query call to transaction`() {
        val restTemplate = TestRestTemplate()

        restTemplate.getForEntity("http://localhost:$port/dsproxy", String::class.java)

        await.untilAsserted {
            verify(transport, atLeastOnce()).send(checkTransaction { transaction ->
                assertThat(transaction.spans).hasSize(1)
                val span = transaction.spans.first()
                assertThat(span.op).isEqualTo("db.query")
                assertThat(span.description).isEqualTo(TracingController.QUERY)
            })
        }
    }
}

@EnableAutoConfiguration(exclude = [SecurityAutoConfiguration::class, SentryP6SpyAutoConfiguration::class])
@SpringBootConfiguration
@Import(TracingController::class)
open class TracingApp

@RestController
class TracingController(private val jdbcTemplate: JdbcTemplate) {
    companion object {
        val QUERY = "select count(*) from INFORMATION_SCHEMA.SYSTEM_USERS where 1=0"
    }

    @GetMapping("/dsproxy")
    fun dsProxy() {
        jdbcTemplate.queryForObject(QUERY, Long::class.java)
    }
}
