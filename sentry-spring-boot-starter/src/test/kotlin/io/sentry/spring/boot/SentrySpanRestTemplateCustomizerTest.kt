package io.sentry.spring.boot

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.IHub
import io.sentry.Scope
import io.sentry.ScopeCallback
import io.sentry.SentryOptions
import io.sentry.SentryTransaction
import io.sentry.TransactionContexts
import kotlin.test.Test
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.core.IsNull
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers
import org.springframework.test.web.client.response.MockRestResponseCreators
import org.springframework.web.client.RestTemplate

class SentrySpanRestTemplateCustomizerTest {
    class Fixture {
        val hub = mock<IHub>()
        val restTemplate = RestTemplate()
        var mockServer = MockRestServiceServer.createServer(restTemplate)
        val transaction = SentryTransaction("aTransaction", TransactionContexts(), hub)
        internal val customizer = SentrySpanRestTemplateCustomizer(hub)

        fun getSut(isTransactionActive: Boolean): RestTemplate {
            customizer.customize(restTemplate)

            if (isTransactionActive) {
                val scope = Scope(SentryOptions())
                scope.setTransaction(transaction)
                whenever(hub.configureScope(any())).thenAnswer {
                    (it.arguments[0] as ScopeCallback).run(scope)
                }

                mockServer.expect(MockRestRequestMatchers.requestTo("/test/123"))
                    .andExpect(MockRestRequestMatchers.method(HttpMethod.GET))
                    .andExpect(MockRestRequestMatchers.header("sentry-trace", IsNull.notNullValue()))
                    .andRespond(MockRestResponseCreators.withSuccess("OK", MediaType.APPLICATION_JSON))
            } else {
                mockServer.expect(MockRestRequestMatchers.requestTo("/test/123"))
                    .andExpect(MockRestRequestMatchers.method(HttpMethod.GET))
                    .andRespond(MockRestResponseCreators.withSuccess("OK", MediaType.APPLICATION_JSON))
            }

            return restTemplate
        }
    }

    private val fixture = Fixture()

    @Test
    fun `when transaction is active, creates span around RestTemplate HTTP call`() {
        val result = fixture.getSut(isTransactionActive = true).getForObject("/test/{id}", String::class.java, 123)

        assertThat(result).isEqualTo("OK")
        assertThat(fixture.transaction.spans).hasSize(1)
        val span = fixture.transaction.spans.first()
        assertThat(span.op).isEqualTo("http")
        assertThat(span.description).isEqualTo("GET /test/{id}")
        fixture.mockServer.verify()
    }

    @Test
    fun `when transaction is not active, does not create span around RestTemplate HTTP call`() {
        val result = fixture.getSut(isTransactionActive = false).getForObject("/test/{id}", String::class.java, 123)

        assertThat(result).isEqualTo("OK")
        assertThat(fixture.transaction.spans).isEmpty()
        fixture.mockServer.verify()
    }

    @Test
    fun `avoids duplicate registration`() {
        val restTemplate = fixture.getSut(isTransactionActive = true)

        fixture.customizer.customize(restTemplate)
        assertThat(restTemplate.interceptors).hasSize(1)
        fixture.customizer.customize(restTemplate)
        assertThat(restTemplate.interceptors).hasSize(1)
    }

    @Test
    fun `normalizes URI to contain leading slash`() {
        val result = fixture.getSut(isTransactionActive = true).getForObject("test/{id}", String::class.java, 123)

        assertThat(result).isEqualTo("OK")
        assertThat(fixture.transaction.spans.first().description).isEqualTo("GET /test/{id}")
        fixture.mockServer.verify()
    }
}
