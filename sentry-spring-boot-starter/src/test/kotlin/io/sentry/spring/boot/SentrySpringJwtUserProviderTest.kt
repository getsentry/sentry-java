package io.sentry.spring.boot

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.SentryOptions
import kotlin.test.Test
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SentrySpringJwtUserProviderTest {

    class Fixture {
        fun getSut(isSendDefaultPii: Boolean = true, jwt: Jwt? = null): SentrySpringJwtUserProvider {
            val options = SentryOptions().apply {
                this.isSendDefaultPii = isSendDefaultPii
            }
            val securityContext = mock<SecurityContext>()
            if (jwt != null) {
                val authentication = mock<Authentication>()
                whenever(securityContext.authentication).thenReturn(authentication)
                whenever(authentication.principal).thenReturn(jwt)
            } else {
                whenever(securityContext.authentication).thenReturn(null)
            }
            SecurityContextHolder.setContext(securityContext)
            return SentrySpringJwtUserProvider(options)
        }

        fun getJwt(tokenValue: String, subject: String, issuer: String, issuedAt: Instant, expiresAt: Instant, audience: Collection<String>, headers: Map<String, Any>, claims: Map<String, Any>): Jwt {
            return Jwt.withTokenValue(tokenValue)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(subject)
                .issuer(issuer)
                .audience(audience)
                .claims {
                    it.putAll(claims)
                }
                .headers {
                    it.putAll(headers)
                }
                .build()
        }
    }

    private val fixture = Fixture()

    @Test
    fun `when send default pii is set to true, returns fully populated user`() {
        val jwt = fixture.getJwt(
            tokenValue = "token",
            subject = "subject",
            issuer = "issuer",
            issuedAt = Instant.MIN,
            expiresAt = Instant.MAX,
            audience = listOf("audience"),
            headers = mapOf("header" to "value"),
            claims = mapOf("email" to "info@sentry.io")
        )
        val provider = fixture.getSut(true, jwt)
        val user = provider.provideUser()
        assertNotNull(user) {
            assertEquals("info@sentry.io", it.username)
            assertEquals("info@sentry.io", it.email)
            assertEquals("subject", it.id)
            assertEquals(jwt.claims, it.others)
        }
    }

    @Test
    fun `when send default pii is set to false, returns null`() {
        val provider = fixture.getSut(false)
        val user = provider.provideUser()
        assertNull(user)
    }

    @Test
    fun `when send default pii is set to true and security context is not set, returns null`() {
        val provider = fixture.getSut(true)
        val user = provider.provideUser()
        assertNull(user)
    }
}
