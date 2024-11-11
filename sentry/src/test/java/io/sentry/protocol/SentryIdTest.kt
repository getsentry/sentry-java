package io.sentry.protocol

import io.sentry.util.StringUtils
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class SentryIdTest {

    @Test
    fun `does not throw when instantiated with corrupted UUID`() {
        val id = SentryId("0000-0000")
        assertEquals("00000000000000000000000000000000", id.toString())
    }

    @Test
    fun `UUID is not generated on initialization`() {
        val uuid = UUID.randomUUID()
        Mockito.mockStatic(UUID::class.java).use { utils ->
            utils.`when`<UUID> { UUID.randomUUID() }.thenReturn(uuid)
            val ignored = SentryId()
            utils.verify({ UUID.randomUUID() }, never())
        }
    }

    @Test
    fun `UUID is generated only once`() {
        val uuid = UUID.randomUUID()
        Mockito.mockStatic(UUID::class.java).use { utils ->
            utils.`when`<UUID> { UUID.randomUUID() }.thenReturn(uuid)
            val sentryId = SentryId()
            val uuid1 = sentryId.toString()
            val uuid2 = sentryId.toString()

            assertEquals(uuid1, uuid2)
            utils.verify({ UUID.randomUUID() }, times(1))
        }
    }

    @Test
    fun `normalizeUUID is only called once`() {
        Mockito.mockStatic(StringUtils::class.java).use { utils ->
            utils.`when`<Any> { StringUtils.normalizeUUID(any()) }.thenReturn("00000000000000000000000000000000")
            val sentryId = SentryId()
            val uuid1 = sentryId.toString()
            val uuid2 = sentryId.toString()

            assertEquals(uuid1, uuid2)
            utils.verify({ StringUtils.normalizeUUID(any()) }, times(1))
        }
    }
}
