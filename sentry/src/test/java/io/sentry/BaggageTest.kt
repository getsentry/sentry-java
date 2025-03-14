package io.sentry

import com.github.javafaker.Faker
import io.sentry.Baggage.MAX_BAGGAGE_LIST_MEMBER_COUNT
import io.sentry.Baggage.MAX_BAGGAGE_STRING_LENGTH
import io.sentry.protocol.SentryId
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BaggageTest {
    lateinit var logger: ILogger

    @BeforeTest
    fun setup() {
        logger = NoOpLogger.getInstance()
    }

    @Test
    fun `can parse single baggage string with white spaces in it`() {
        val baggage = Baggage.fromHeader("sentry-userId =  alice   ,  sentry-serverNode = DF%2028,sentry-isProduction=false", false, logger)

        assertEquals("alice", baggage.get("sentry-userId"))
        assertEquals("DF 28", baggage.get("sentry-serverNode"))
        assertEquals("false", baggage.get("sentry-isProduction"))

        assertEquals("sentry-isProduction=false,sentry-serverNode=DF%2028,sentry-userId=alice", baggage.toHeaderString(null))
    }

    @Test
    fun `retain single third party baggage string`() {
        val baggage = Baggage.fromHeader("userId=alice,serverNode=DF%2028,isProduction=false", true, logger)

        assertEquals("userId=alice,serverNode=DF%2028,isProduction=false", baggage.toHeaderString(baggage.getThirdPartyHeader()))
    }

    @Test
    fun `sentry values in third party baggage string are removed`() {
        val baggage = Baggage.fromHeader("userId=alice,sentry-thirdParty=thirdPartyValue,serverNode=DF%2028,isProduction=false", true, logger)

        assertEquals("userId=alice,serverNode=DF%2028,isProduction=false", baggage.getThirdPartyHeader())
    }

    @Test
    fun `third party headers are dropped if not specified to keep`() {
        val baggage = Baggage.fromHeader("userId=alice,sentry-serverNode=DF%2028,isProduction=false", logger)

        assertEquals("sentry-serverNode=DF%2028", baggage.toHeaderString(null))
    }

    @Test
    fun `multiple baggage headers are merged into one, third party headers are dropped`() {
        val headerValues = listOf(
            "userId=alice,sentry-serverNode=DF%2028,isProduction=false",
            "sentry-environment=production, os=android",
            "trace=123456, isSampled=true"
        )

        val baggage = Baggage.fromHeader(headerValues, logger)

        assertEquals("sentry-environment=production,sentry-serverNode=DF%2028", baggage.toHeaderString(null))
    }

    @Test
    fun `sentry entries are stripped from third party headers and saved in original order`() {
        val headerValues = listOf(
            "userId=alice,sentry-serverNode=DF%2028,isProduction=false",
            "sentry-environment=production, os=android",
            "trace=123456, isSampled=true"
        )

        val baggage = Baggage.fromHeader(headerValues, true, logger)

        assertEquals("userId=alice,isProduction=false,os=android,trace=123456,isSampled=true", baggage.thirdPartyHeader)
    }

    @Test
    fun `when merging incoming and thirdparty baggages, sentry values are appended at the end`() {
        val headerValues = listOf(
            "userId=alice,sentry-serverNode=DF%2028,isProduction=false",
            "sentry-environment=production, os=android",
            "trace=123456, isSampled=true"
        )

        val baggage = Baggage.fromHeader(headerValues, false, logger)
        val thirdPartyBaggage = Baggage.fromHeader(headerValues, true, logger)

        assertEquals("userId=alice,isProduction=false,os=android,trace=123456,isSampled=true,sentry-environment=production,sentry-serverNode=DF%2028", baggage.toHeaderString(thirdPartyBaggage.thirdPartyHeader))
    }

    @Test
    fun `when merging and thirdparty headers already use up the available space, sentry values are not added`() {
        val largeThirdPartyHeaderValue = Faker.instance().random().hex(MAX_BAGGAGE_STRING_LENGTH - 16 - 12 - 1) // 8192 - "large-value=" - "otherValue=value" - ","

        val incomingHeaderValues = listOf(
            "userId=alice,sentry-serverNode=DF%2028,isProduction=false",
            "sentry-environment=production, os=android",
            "trace=123456, isSampled=true"
        )

        val thirdPartyHeaderValues = "large-value=$largeThirdPartyHeaderValue, otherValue=value"

        val baggage = Baggage.fromHeader(incomingHeaderValues, false, logger)
        val thirdPartyBaggage = Baggage.fromHeader(thirdPartyHeaderValues, true, logger)

        val headerString = baggage.toHeaderString(thirdPartyBaggage.thirdPartyHeader)
        assertEquals(MAX_BAGGAGE_STRING_LENGTH, headerString.length)
        assertEquals("large-value=$largeThirdPartyHeaderValue,otherValue=value", headerString)
    }

    @Test
    fun `when merging third party baggage is kept even if it exceeds the allowed max length`() {
        val largeThirdPartyHeaderValue = Faker.instance().random().hex(MAX_BAGGAGE_STRING_LENGTH)

        val headerValues = listOf(
            "userId=alice,sentry-serverNode=DF%2028,isProduction=false",
            "sentry-environment=production, os=android",
            "trace=123456, isSampled=true"
        )

        val baggage = Baggage.fromHeader(headerValues, false, logger)
        val thirdPartyBaggage = Baggage.fromHeader("largeHeader=$largeThirdPartyHeaderValue", true, logger)
        val headerString = baggage.toHeaderString(thirdPartyBaggage.thirdPartyHeader)
        assertEquals(MAX_BAGGAGE_STRING_LENGTH + 12, headerString.length)
        assertEquals("largeHeader=$largeThirdPartyHeaderValue", headerString)
    }

    @Test
    fun `exceeding entry limit causes values to be dropped`() {
        val baggage = Baggage(logger)
        val expectedItems = mutableListOf<String>()

        for (i in 1..100) {
            val key = 100 + i
            baggage.set("a$key", "$i")
            if (i <= MAX_BAGGAGE_LIST_MEMBER_COUNT) {
                expectedItems.add("a$key=$i")
            }
        }

        val expectedHeaderString = expectedItems.joinToString(",")
        assertEquals(expectedHeaderString, baggage.toHeaderString(null))
    }

    @Test
    fun `if third party header exceeds entry limit, sentry headers are not added`() {
        val baggage = Baggage(logger)
        baggage.set("sentry-one", "value1")
        baggage.set("sentry-two", "value2")
        val thirdPartyItems = mutableListOf<String>()

        for (i in 1..70) {
            val key = 70 + i
            thirdPartyItems.add("a$key=$i")
        }

        val thirdPartyHeaderString = thirdPartyItems.joinToString(",")
        assertEquals(thirdPartyHeaderString, baggage.toHeaderString(thirdPartyHeaderString))
    }

    @Test
    fun `if third party header is close to entry limit only some sentry headers are added`() {
        val baggage = Baggage(logger)
        baggage.set("sentry-one", "value1")
        baggage.set("sentry-two", "value2")
        val thirdPartyItems = mutableListOf<String>()

        for (i in 1..63) {
            val key = 63 + i
            thirdPartyItems.add("a$key=$i")
        }

        val thirdPartyHeaderString = thirdPartyItems.joinToString(",")
        val expectedHeaderString = "$thirdPartyHeaderString,sentry-one=value1"
        assertEquals(expectedHeaderString, baggage.toHeaderString(thirdPartyHeaderString))
    }

    @Test
    fun `keys are encoded and decoded as well`() {
        val baggage = Baggage.fromHeader("sentry-user%2Bid=alice,sentry-server%2Bnode=DF%2028,sentry-is%2Bproduction=false", logger)

        assertEquals("alice", baggage.get("sentry-user+id"))
        assertEquals("DF 28", baggage.get("sentry-server+node"))
        assertEquals("false", baggage.get("sentry-is+production"))

        assertEquals("sentry-is%2Bproduction=false,sentry-server%2Bnode=DF%2028,sentry-user%2Bid=alice", baggage.toHeaderString(null))
    }

    @Test
    fun `can parse multiple baggage strings`() {
        val baggage = Baggage.fromHeader(
            listOf(
                "sentry-userId=alice",
                "sentry-serverNode=DF%2028,sentry-isProduction=false"
            ),
            logger
        )

        assertEquals("alice", baggage.get("sentry-userId"))
        assertEquals("DF 28", baggage.get("sentry-serverNode"))
        assertEquals("false", baggage.get("sentry-isProduction"))

        assertEquals("sentry-isProduction=false,sentry-serverNode=DF%2028,sentry-userId=alice", baggage.toHeaderString(null))
    }

    @Test
    fun `can parse multiple baggage strings with white spaces`() {
        val baggage = Baggage.fromHeader(
            listOf(
                "sentry-userId =   alice",
                "sentry-serverNode = DF%2028, sentry-isProduction = false"
            ),
            logger
        )

        assertEquals("alice", baggage.get("sentry-userId"))
        assertEquals("DF 28", baggage.get("sentry-serverNode"))
        assertEquals("false", baggage.get("sentry-isProduction"))

        assertEquals("sentry-isProduction=false,sentry-serverNode=DF%2028,sentry-userId=alice", baggage.toHeaderString(null))
    }

    @Test
    fun `can parse null baggage string`() {
        val nothing: String? = null
        val baggage = Baggage.fromHeader(nothing, logger)
        assertEquals("", baggage.toHeaderString(null))
    }

    @Test
    fun `can parse blank baggage string`() {
        val baggage = Baggage.fromHeader("", logger)
        assertEquals("", baggage.toHeaderString(null))
    }

    @Test
    fun `can parse whitespace only baggage string`() {
        val baggage = Baggage.fromHeader("   ", logger)
        assertEquals("", baggage.toHeaderString(null))
    }

    @Test
    fun `can parse whitespace only baggage strings`() {
        val baggage = Baggage.fromHeader(listOf("   ", "   "), logger)
        assertEquals("", baggage.toHeaderString(null))
    }

    @Test
    fun `single large value is dropped and small values are kept`() {
        val largeValue = Faker.instance().random().hex(8193)
        val baggage = Baggage.fromHeader("sentry-smallValue=remains,sentry-largeValue=$largeValue,sentry-otherValue=kept", logger)

        assertEquals("remains", baggage.get("sentry-smallValue"))
        assertNotNull(baggage.get("sentry-largeValue"))
        assertEquals("kept", baggage.get("sentry-otherValue"))

        assertEquals("sentry-otherValue=kept,sentry-smallValue=remains", baggage.toHeaderString(null))
    }

    @Test
    fun `medium size value can cause small values to be dropped`() {
        val mediumValue = Faker.instance().random().hex(MAX_BAGGAGE_STRING_LENGTH - 19 - 22 - 1) // 8192 - "sentry-mediumValue=" - "sentry-otherValue=kept" - ","
        val baggage = Baggage.fromHeader("sentry-mediumValue=$mediumValue,sentry-smallValue=removed,sentry-otherValue=kept", logger)

        assertEquals("removed", baggage.get("sentry-smallValue"))
        assertEquals(mediumValue, baggage.get("sentry-mediumValue"))
        assertEquals("kept", baggage.get("sentry-otherValue"))

        val headerString = baggage.toHeaderString(null)
        assertEquals(MAX_BAGGAGE_STRING_LENGTH, headerString.length)
        assertEquals("sentry-mediumValue=$mediumValue,sentry-otherValue=kept", headerString)
    }

    @Test
    fun `medium size value can cause all values to be dropped`() {
        // nothing else will fit after mediumValue as the separator + any key/value would exceed the limit
        val mediumValue = Faker.instance().random().hex(MAX_BAGGAGE_STRING_LENGTH - 19 - 22) // 8192 - "sentry-mediumValue=" - "sentry-otherValue=lost"
        val baggage = Baggage.fromHeader("sentry-mediumValue=$mediumValue,sentry-smallValue=stripped,sentry-otherValue=lost", logger)

        assertEquals("stripped", baggage.get("sentry-smallValue"))
        assertEquals(mediumValue, baggage.get("sentry-mediumValue"))
        assertEquals("lost", baggage.get("sentry-otherValue"))

        val headerString = baggage.toHeaderString(null)
        assertEquals(8170, headerString.length)
        assertEquals("sentry-mediumValue=$mediumValue", headerString)
    }

    @Test
    fun `null value is omitted from header string`() {
        val baggage = Baggage(logger)

        baggage.traceId = null
        baggage.publicKey = null
        baggage.release = null
        baggage.environment = null
        baggage.transaction = null
        baggage.userId = null

        assertEquals("", baggage.toHeaderString(null))
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
        baggage.setSampleRate((1.0 / 3.0).toString())
        baggage.setSampled("true")

        assertEquals("sentry-environment=production,sentry-public_key=$publicKey,sentry-release=1.0-rc.1,sentry-sample_rate=0.3333333333333333,sentry-sampled=true,sentry-trace_id=$traceId,sentry-transaction=TX,sentry-user_id=$userId", baggage.toHeaderString(null))
    }

    @Test
    fun `duplicate entries are lost`() {
        val baggage = Baggage.fromHeader("sentry-duplicate=a,sentry-duplicate=b", logger)
        assertEquals("sentry-duplicate=b", baggage.toHeaderString(null))
    }

    @Test
    fun `setting a value multiple times only keeps the last`() {
        val baggage = Baggage.fromHeader("", logger)

        baggage.traceId = "a"
        baggage.traceId = "b"
        baggage.traceId = "c"

        assertEquals("sentry-trace_id=c", baggage.toHeaderString(null))
    }

    @Test
    fun `setting values on frozen baggage has no effect`() {
        val baggage = Baggage.fromHeader("sentry-trace_id=a,sentry-transaction=sentryTransaction", logger)
        baggage.freeze()

        baggage.traceId = "b"
        baggage.traceId = "c"

        baggage.transaction = "newTransaction"
        baggage.environment = "production"

        assertEquals("sentry-trace_id=a,sentry-transaction=sentryTransaction", baggage.toHeaderString(null))
    }

    @Test
    fun `if header contains sentry values baggage is marked as shouldFreeze`() {
        val baggage = Baggage.fromHeader("sentry-trace_id=a,sentry-transaction=sentryTransaction", logger)
        assertTrue(baggage.isShouldFreeze)
    }

    @Test
    fun `if header does not contain sentry values baggage is not marked as shouldFreeze`() {
        val baggage = Baggage.fromHeader("a=b", logger)
        assertFalse(baggage.isShouldFreeze)
    }

    @Test
    fun `value may contain = sign`() {
        val baggage = Baggage(logger)

        baggage.setTransaction("a=b")

        assertEquals("sentry-transaction=a%3Db", baggage.toHeaderString(null))
    }

    @Test
    fun `corrupted string does not throw out`() {
        val baggage = Baggage.fromHeader("a", logger)
        assertEquals("", baggage.toHeaderString(null))
    }

    @Test
    fun `corrupted string does not throw out 2`() {
        val baggage = Baggage.fromHeader("a=b=", logger)
        assertEquals("", baggage.toHeaderString(null))
    }

    @Test
    fun `corrupted string can be parsed partially`() {
        val baggage = Baggage.fromHeader("sentry-a=value,sentry-b", logger)
        assertEquals("sentry-a=value", baggage.toHeaderString(null))
    }

    @Test
    fun `baggage value encoding`() {
        // keep baggage-octet: %x21 / %x23-2B / %x2D-3A / %x3C-5B / %x5D-7E
        val values = percentEncodedValues().also {
            /* some characters have been commented out
             * as per baggage specification we are allowed to encode these
             * and java.net.URLEncoder encodes them by default
             */
//            it["!"] = "!" // instead of %21
//            it["#"] = "#" // instead of %23
//            it["$"] = "$" // instead of %24
//            it["%"] = "%" // instead of %25
//            it["&"] = "&" // instead of %26
//            it["'"] = "'" // instead of %27
//            it["("] = "(" // instead of %28
//            it[")"] = ")" // instead of %29
            it["*"] = "*" // instead of %2A
//            it["+"] = "+" // instead of %2B
            it["-"] = "-" // instead of %2D
            it["."] = "." // instead of %2E
//            it["/"] = "/" // instead of %2F
            it["0"] = "0" // instead of %30
            it["1"] = "1" // instead of %31
            it["2"] = "2" // instead of %32
            it["3"] = "3" // instead of %33
            it["4"] = "4" // instead of %34
            it["5"] = "5" // instead of %35
            it["6"] = "6" // instead of %36
            it["7"] = "7" // instead of %37
            it["8"] = "8" // instead of %38
            it["9"] = "9" // instead of %39
//            it[":"] = ":" // instead of %3A
//            it["<"] = "<" // instead of %3C
//            it["="] = "=" // instead of %3D
//            it[">"] = ">" // instead of %3E
//            it["?"] = "?" // instead of %3F
//            it["@"] = "@" // instead of %40
            it["A"] = "A" // instead of %41
            it["B"] = "B" // instead of %42
            it["C"] = "C" // instead of %43
            it["D"] = "D" // instead of %44
            it["E"] = "E" // instead of %45
            it["F"] = "F" // instead of %46
            it["G"] = "G" // instead of %47
            it["H"] = "H" // instead of %48
            it["I"] = "I" // instead of %49
            it["J"] = "J" // instead of %4A
            it["K"] = "K" // instead of %4B
            it["L"] = "L" // instead of %4C
            it["M"] = "M" // instead of %4D
            it["N"] = "N" // instead of %4E
            it["O"] = "O" // instead of %4F
            it["P"] = "P" // instead of %50
            it["Q"] = "Q" // instead of %51
            it["R"] = "R" // instead of %52
            it["S"] = "S" // instead of %53
            it["T"] = "T" // instead of %54
            it["U"] = "U" // instead of %55
            it["V"] = "V" // instead of %56
            it["W"] = "W" // instead of %57
            it["X"] = "X" // instead of %58
            it["Y"] = "Y" // instead of %59
            it["Z"] = "Z" // instead of %5A
//            it["["] = "[" // instead of %5B
//            it["]"] = "]" // instead of %5D
//            it["^"] = "^" // instead of %5E
            it["_"] = "_" // instead of %5F
//            it["`"] = "`" // instead of %60
            it["a"] = "a" // instead of %61
            it["b"] = "b" // instead of %62
            it["c"] = "c" // instead of %63
            it["d"] = "d" // instead of %64
            it["e"] = "e" // instead of %65
            it["f"] = "f" // instead of %66
            it["g"] = "g" // instead of %67
            it["h"] = "h" // instead of %68
            it["i"] = "i" // instead of %69
            it["j"] = "j" // instead of %6A
            it["k"] = "k" // instead of %6B
            it["l"] = "l" // instead of %6C
            it["m"] = "m" // instead of %6D
            it["n"] = "n" // instead of %6E
            it["o"] = "o" // instead of %6F
            it["p"] = "p" // instead of %70
            it["q"] = "q" // instead of %71
            it["r"] = "r" // instead of %72
            it["s"] = "s" // instead of %73
            it["t"] = "t" // instead of %74
            it["u"] = "u" // instead of %75
            it["v"] = "v" // instead of %76
            it["w"] = "w" // instead of %77
            it["x"] = "x" // instead of %78
            it["y"] = "y" // instead of %79
            it["z"] = "z" // instead of %7A
        }

        val failures = mutableListOf<String>()

        values.forEach { key, value ->
            val baggage = Baggage(logger)
            baggage.setTransaction(key)

            val headerString = baggage.toHeaderString(null)
            if ("sentry-transaction=$value" != headerString) {
                failures.add("$key should be $value but was >$headerString<")
            }

            val decodedBaggage = Baggage.fromHeader(headerString, logger)
            decodedBaggage.get("sentry-transaction")
        }

        assertTrue(failures.joinToString("\n")) { failures.isEmpty() }
    }

    @Test
    fun `all characters defined as valid for keys can be used`() {
        val baggage = Baggage(logger)
        val key = "sentry-" + validTokenCharacters().joinToString("")
        baggage.set(key, "value")

        val reparsedBaggage = Baggage.fromHeader(baggage.toHeaderString(null), logger)
        assertEquals("value", reparsedBaggage.get(key))
    }

    @Test
    fun `baggage key replaces invalid characters`() {
        val baggage = Baggage(logger)
        baggage.set(invalidTokenCharacters().joinToString(""), "value")

        assertEquals("%22%28%29%2C%2F%3A%3B%3C%3D%3E%3F%40%5B%5C%5D%7B%7D=value", baggage.toHeaderString(null))
    }

    @Test
    fun `can skip logger for header from single string`() {
        val baggage = Baggage.fromHeader("sentry-trace_id=a,sentry-transaction=sentryTransaction")
        assertEquals("sentry-trace_id=a,sentry-transaction=sentryTransaction", baggage.toHeaderString(null))
    }

    @Test
    fun `can skip logger for header from list of strings`() {
        val baggage = Baggage.fromHeader(listOf("sentry-trace_id=a", "sentry-transaction=sentryTransaction"))
        assertEquals("sentry-trace_id=a,sentry-transaction=sentryTransaction", baggage.toHeaderString(null))
    }

    @Test
    fun `unknown returns sentry- prefixed keys that are not known and passes them on to TraceContext`() {
        val baggage = Baggage.fromHeader(listOf("sentry-trace_id=${SentryId()},sentry-public_key=b, sentry-replay_id=${SentryId()}", "sentry-transaction=sentryTransaction, sentry-anewkey=abc"))
        val unknown = baggage.unknown
        assertEquals(1, unknown.size)
        assertEquals("abc", unknown["anewkey"])

        val traceContext = baggage.toTraceContext()!!
        assertEquals(1, traceContext.unknown!!.size)
        assertEquals("abc", traceContext.unknown!!["anewkey"])
    }

    @Test
    fun `header with sentry values is marked for freezing`() {
        val baggage =
            Baggage.fromHeader("sentry-trace_id=a,sentry-transaction=sentryTransaction")
        assertTrue(baggage.isShouldFreeze)
    }

    @Test
    fun `header with sentry sample rand only is not marked for freezing`() {
        val baggage =
            Baggage.fromHeader("sentry-sample_rand=0.3")
        assertFalse(baggage.isShouldFreeze)
    }

    @Test
    fun `header without sentry values is not marked for freezing`() {
        val baggage =
            Baggage.fromHeader("a=b,c=d")
        assertFalse(baggage.isShouldFreeze)
    }

    @Test
    fun `sets values from traces sampling decision`() {
        val baggage = Baggage.fromHeader("a=b,c=d")
        baggage.setValuesFromSamplingDecision(TracesSamplingDecision(true, 0.021, 0.025))

        assertEquals("true", baggage.sampled)
        assertEquals("0.021", baggage.sampleRate)
        assertEquals("0.025", baggage.sampleRand)
    }

    @Test
    fun `handles null traces sampling decision`() {
        val baggage = Baggage.fromHeader("a=b,c=d")
        baggage.setValuesFromSamplingDecision(null)
    }

    @Test
    fun `sets values from traces sampling decision only if non null`() {
        val baggage = Baggage.fromHeader("a=b,c=d")
        baggage.setValuesFromSamplingDecision(TracesSamplingDecision(true, 0.021, 0.025))
        baggage.setValuesFromSamplingDecision(TracesSamplingDecision(false, null, null))

        assertEquals("false", baggage.sampled)
        assertEquals("0.021", baggage.sampleRate)
        assertEquals("0.025", baggage.sampleRand)
    }

    @Test
    fun `replaces only sample rate if already frozen`() {
        val baggage = Baggage.fromHeader("a=b,c=d")
        baggage.setValuesFromSamplingDecision(TracesSamplingDecision(true, 0.021, 0.025))
        baggage.freeze()
        baggage.setValuesFromSamplingDecision(TracesSamplingDecision(false, 0.121, 0.125))

        assertEquals("true", baggage.sampled)
        assertEquals("0.121", baggage.sampleRate)
        assertEquals("0.025", baggage.sampleRand)
    }

    fun `sample rate can be retrieved as double`() {
        val baggage = Baggage.fromHeader("a=b,c=d")
        baggage.sampleRate = "0.1"
        assertEquals(0.1, baggage.sampleRateDouble)
    }

    @Test
    fun `sample rand can be retrieved as double`() {
        val baggage = Baggage.fromHeader("a=b,c=d")
        baggage.sampleRand = "0.1"
        assertEquals(0.1, baggage.sampleRandDouble)
    }

    @Test
    fun `sample rand can be set as double`() {
        val baggage = Baggage.fromHeader("a=b,c=d")
        baggage.sampleRandDouble = 0.1
        assertEquals("0.1", baggage.sampleRand)
    }

    @Test
    fun `broken sample rand returns null double`() {
        val baggage = Baggage.fromHeader("a=b,c=d")
        baggage.sampleRand = "a0.1"
        assertNull(baggage.sampleRandDouble)
    }

    @Test
    fun `broken sample rate returns null double`() {
        val baggage = Baggage.fromHeader("a=b,c=d")
        baggage.sampleRate = "a0.1"
        assertNull(baggage.sampleRateDouble)
    }

    /**
     * token          = 1*tchar
     * tchar          = "!" / "#" / "$" / "%" / "&" / "'" / "*"
     *                / "+" / "-" / "." / "^" / "_" / "`" / "|" / "~"
     *                / DIGIT / ALPHA
     *                ; any VCHAR, except delimiters
     */
    private fun validTokenCharacters() = mutableListOf(
        "!",
        "#",
        "$",
        "%",
        "&",
        "'",
        "*",
        "+",
        "-",
        ".",
        "^",
        "_",
        "`",
        "|",
        "~",
        "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10",
        "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z",
        "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z"
    )

    private fun invalidTokenCharacters() = mutableListOf(
        "\"",
        "(",
        ")",
        ",",
        "/",
        ":",
        ";",
        "<",
        "=",
        ">",
        "?",
        "@",
        "[",
        "\\",
        "]",
        "{",
        "}"
    )

    private fun percentEncodedValues() = mutableMapOf(
        " " to "%20",
        "!" to "%21",
        "\"" to "%22",
        "#" to "%23",
        "$" to "%24",
        "%" to "%25",
        "&" to "%26",
        "'" to "%27",
        "(" to "%28",
        ")" to "%29",
        "*" to "%2A",
        "+" to "%2B",
        "," to "%2C",
        "-" to "%2D",
        "." to "%2E",
        "/" to "%2F",
        "0" to "%30",
        "1" to "%31",
        "2" to "%32",
        "3" to "%33",
        "4" to "%34",
        "5" to "%35",
        "6" to "%36",
        "7" to "%37",
        "8" to "%38",
        "9" to "%39",
        ":" to "%3A",
        ";" to "%3B",
        "<" to "%3C",
        "=" to "%3D",
        ">" to "%3E",
        "?" to "%3F",
        "@" to "%40",
        "A" to "%41",
        "B" to "%42",
        "C" to "%43",
        "D" to "%44",
        "E" to "%45",
        "F" to "%46",
        "G" to "%47",
        "H" to "%48",
        "I" to "%49",
        "J" to "%4A",
        "K" to "%4B",
        "L" to "%4C",
        "M" to "%4D",
        "N" to "%4E",
        "O" to "%4F",
        "P" to "%50",
        "Q" to "%51",
        "R" to "%52",
        "S" to "%53",
        "T" to "%54",
        "U" to "%55",
        "V" to "%56",
        "W" to "%57",
        "X" to "%58",
        "Y" to "%59",
        "Z" to "%5A",
        "[" to "%5B",
        "\\" to "%5C",
        "]" to "%5D",
        "^" to "%5E",
        "_" to "%5F",
        "`" to "%60",
        "a" to "%61",
        "b" to "%62",
        "c" to "%63",
        "d" to "%64",
        "e" to "%65",
        "f" to "%66",
        "g" to "%67",
        "h" to "%68",
        "i" to "%69",
        "j" to "%6A",
        "k" to "%6B",
        "l" to "%6C",
        "m" to "%6D",
        "n" to "%6E",
        "o" to "%6F",
        "p" to "%70",
        "q" to "%71",
        "r" to "%72",
        "s" to "%73",
        "t" to "%74",
        "u" to "%75",
        "v" to "%76",
        "w" to "%77",
        "x" to "%78",
        "y" to "%79",
        "z" to "%7A",
        "{" to "%7B",
        "|" to "%7C",
        "}" to "%7D",
        "~" to "%7E",
        "\u007F" to "%7F",
        "€" to "%E2%82%AC",
        "" to "%C2%81",
        "‚" to "%E2%80%9A",
        "ƒ" to "%C6%92",
        "„" to "%E2%80%9E",
        "…" to "%E2%80%A6",
        "†" to "%E2%80%A0",
        "‡" to "%E2%80%A1",
        "ˆ" to "%CB%86",
        "‰" to "%E2%80%B0",
        "Š" to "%C5%A0",
        "‹" to "%E2%80%B9",
        "Œ" to "%C5%92",
        "" to "%C2%8D",
        "Ž" to "%C5%BD",
        "" to "%C2%8F",
        "" to "%C2%90",
        "‘" to "%E2%80%98",
        "’" to "%E2%80%99",
        "“" to "%E2%80%9C",
        "”" to "%E2%80%9D",
        "•" to "%E2%80%A2",
        "–" to "%E2%80%93",
        "—" to "%E2%80%94",
        "˜" to "%CB%9C",
        "™" to "%E2%84%A2",
        "š" to "%C5%A1",
        "›" to "%E2%80%BA",
        "œ" to "%C5%93",
        "" to "%C2%9D",
        "ž" to "%C5%BE",
        "Ÿ" to "%C5%B8",
        "\u00A0" to "%C2%A0", // nbsp
        "¡" to "%C2%A1",
        "¢" to "%C2%A2",
        "£" to "%C2%A3",
        "¤" to "%C2%A4",
        "¥" to "%C2%A5",
        "¦" to "%C2%A6",
        "§" to "%C2%A7",
        "¨" to "%C2%A8",
        "©" to "%C2%A9",
        "ª" to "%C2%AA",
        "«" to "%C2%AB",
        "¬" to "%C2%AC",
        "­" to "%C2%AD",
        "®" to "%C2%AE",
        "¯" to "%C2%AF",
        "°" to "%C2%B0",
        "±" to "%C2%B1",
        "²" to "%C2%B2",
        "³" to "%C2%B3",
        "´" to "%C2%B4",
        "µ" to "%C2%B5",
        "¶" to "%C2%B6",
        "·" to "%C2%B7",
        "¸" to "%C2%B8",
        "¹" to "%C2%B9",
        "º" to "%C2%BA",
        "»" to "%C2%BB",
        "¼" to "%C2%BC",
        "½" to "%C2%BD",
        "¾" to "%C2%BE",
        "¿" to "%C2%BF",
        "À" to "%C3%80",
        "Á" to "%C3%81",
        "Â" to "%C3%82",
        "Ã" to "%C3%83",
        "Ä" to "%C3%84",
        "Å" to "%C3%85",
        "Æ" to "%C3%86",
        "Ç" to "%C3%87",
        "È" to "%C3%88",
        "É" to "%C3%89",
        "Ê" to "%C3%8A",
        "Ë" to "%C3%8B",
        "Ì" to "%C3%8C",
        "Í" to "%C3%8D",
        "Î" to "%C3%8E",
        "Ï" to "%C3%8F",
        "Ð" to "%C3%90",
        "Ñ" to "%C3%91",
        "Ò" to "%C3%92",
        "Ó" to "%C3%93",
        "Ô" to "%C3%94",
        "Õ" to "%C3%95",
        "Ö" to "%C3%96",
        "×" to "%C3%97",
        "Ø" to "%C3%98",
        "Ù" to "%C3%99",
        "Ú" to "%C3%9A",
        "Û" to "%C3%9B",
        "Ü" to "%C3%9C",
        "Ý" to "%C3%9D",
        "Þ" to "%C3%9E",
        "ß" to "%C3%9F",
        "à" to "%C3%A0",
        "á" to "%C3%A1",
        "â" to "%C3%A2",
        "ã" to "%C3%A3",
        "ä" to "%C3%A4",
        "å" to "%C3%A5",
        "æ" to "%C3%A6",
        "ç" to "%C3%A7",
        "è" to "%C3%A8",
        "é" to "%C3%A9",
        "ê" to "%C3%AA",
        "ë" to "%C3%AB",
        "ì" to "%C3%AC",
        "í" to "%C3%AD",
        "î" to "%C3%AE",
        "ï" to "%C3%AF",
        "ð" to "%C3%B0",
        "ñ" to "%C3%B1",
        "ò" to "%C3%B2",
        "ó" to "%C3%B3",
        "ô" to "%C3%B4",
        "õ" to "%C3%B5",
        "ö" to "%C3%B6",
        "÷" to "%C3%B7",
        "ø" to "%C3%B8",
        "ù" to "%C3%B9",
        "ú" to "%C3%BA",
        "û" to "%C3%BB",
        "ü" to "%C3%BC",
        "ý" to "%C3%BD",
        "þ" to "%C3%BE",
        "ÿ" to "%C3%BF"
    )
}
