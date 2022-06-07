package io.sentry

import com.github.javafaker.Faker
import io.sentry.protocol.SentryId
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class BaggageTest {
    lateinit var logger: ILogger

    @BeforeTest
    fun setup() {
        logger = NoOpLogger.getInstance()
    }

    @Test
    fun `can parse single baggage string with white spaces in it`() {
        val baggage = Baggage.fromHeader("userId =  alice   ,  serverNode = DF%2028,isProduction=false", logger)

        assertEquals("alice", baggage.get("userId"))
        assertEquals("DF 28", baggage.get("serverNode"))
        assertEquals("false", baggage.get("isProduction"))

        assertEquals("isProduction=false,serverNode=DF%2028,userId=alice", baggage.toHeaderString())
    }
    @Test
    fun `can parse single baggage string`() {
        val baggage = Baggage.fromHeader("userId=alice,serverNode=DF%2028,isProduction=false", logger)

        assertEquals("alice", baggage.get("userId"))
        assertEquals("DF 28", baggage.get("serverNode"))
        assertEquals("false", baggage.get("isProduction"))

        assertEquals("isProduction=false,serverNode=DF%2028,userId=alice", baggage.toHeaderString())
    }

    @Test
    fun `keys are encoded and decoded as well`() {
        val baggage = Baggage.fromHeader("user%2Bid=alice,server%2Bnode=DF%2028,is%2Bproduction=false", logger)

        assertEquals("alice", baggage.get("user+id"))
        assertEquals("DF 28", baggage.get("server+node"))
        assertEquals("false", baggage.get("is+production"))

        assertEquals("is%2Bproduction=false,server%2Bnode=DF%2028,user%2Bid=alice", baggage.toHeaderString())
    }

    @Test
    fun `can parse multiple baggage strings`() {
        val baggage = Baggage.fromHeader(
            listOf(
                "userId=alice",
                "serverNode=DF%2028,isProduction=false"
            ),
            logger
        )

        assertEquals("alice", baggage.get("userId"))
        assertEquals("DF 28", baggage.get("serverNode"))
        assertEquals("false", baggage.get("isProduction"))

        assertEquals("isProduction=false,serverNode=DF%2028,userId=alice", baggage.toHeaderString())
    }

    @Test
    fun `can parse multiple baggage strings with white spaces`() {
        val baggage = Baggage.fromHeader(
            listOf(
                "userId =   alice",
                "serverNode = DF%2028, isProduction = false"
            ),
            logger
        )

        assertEquals("alice", baggage.get("userId"))
        assertEquals("DF 28", baggage.get("serverNode"))
        assertEquals("false", baggage.get("isProduction"))

        assertEquals("isProduction=false,serverNode=DF%2028,userId=alice", baggage.toHeaderString())
    }

    @Test
    fun `can parse null baggage string`() {
        val nothing: String? = null
        val baggage = Baggage.fromHeader(nothing, logger)
        assertEquals("", baggage.toHeaderString())
    }

    @Test
    fun `can parse blank baggage string`() {
        val baggage = Baggage.fromHeader("", logger)
        assertEquals("", baggage.toHeaderString())
    }

    @Test
    fun `can parse whitespace only baggage string`() {
        val baggage = Baggage.fromHeader("   ", logger)
        assertEquals("", baggage.toHeaderString())
    }

    @Test
    fun `can parse whitespace only baggage strings`() {
        val baggage = Baggage.fromHeader(listOf("   ", "   "), logger)
        assertEquals("", baggage.toHeaderString())
    }

    @Test
    fun `if value exceeds size limit key value pair is not added to header`() {
        val largeValue = Faker.instance().random().hex(8193)
        val baggage = Baggage.fromHeader("smallValue=remains,largeValue=$largeValue", logger)

        assertEquals("remains", baggage.get("smallValue"))
        assertNotNull(baggage.get("largeValue"))

        assertEquals("smallValue=remains", baggage.toHeaderString())
    }

    @Test
    fun `null value is omitted from header string`() {
        val baggage = Baggage(logger)

        baggage.setTraceId(null)
        baggage.setPublicKey(null)
        baggage.setRelease(null)
        baggage.setEnvironment(null)
        baggage.setTransaction(null)
        baggage.setUserId(null)
        baggage.setUserSegment(null)

        assertEquals("", baggage.toHeaderString())
    }

    @Test
    fun `can set values from trace context`() {
        val baggage = Baggage(logger)
        val publicKey = Dsn(dsnString).publicKey
        val traceId = SentryId().toString()
        val userId = UUID.randomUUID().toString()

        baggage.setTraceId(traceId)
        baggage.setPublicKey(publicKey)
        baggage.setRelease("1.0-rc.1")
        baggage.setEnvironment("production")
        baggage.setTransaction("TX")
        baggage.setUserId(userId)
        baggage.setUserSegment("segmentA")

        assertEquals("sentry-environment=production,sentry-publickey=$publicKey,sentry-release=1.0-rc.1,sentry-traceid=$traceId,sentry-transaction=TX,sentry-userid=$userId,sentry-usersegment=segmentA", baggage.toHeaderString())
    }

    @Test
    fun `duplicate entries are lost`() {
        val baggage = Baggage.fromHeader("duplicate=a,duplicate=b", logger)
        assertEquals("duplicate=b", baggage.toHeaderString())
    }

    @Test
    fun `setting a value multiple times only keeps the last`() {
        val baggage = Baggage.fromHeader("sentry-traceid=a", logger)

        baggage.setTraceId("b")
        baggage.setTraceId("c")

        assertEquals("sentry-traceid=c", baggage.toHeaderString())
    }

    @Test
    fun `value may contain = sign`() {
        val baggage = Baggage(logger)

        baggage.setTransaction("a=b")

        assertEquals("sentry-transaction=a%3Db", baggage.toHeaderString())
    }
}
