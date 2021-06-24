package io.sentry.json.extensions

import io.sentry.json.stream.JsonReader
import io.sentry.json.stream.JsonToken
import java.io.IOException
import kotlin.jvm.Throws

// NextOrNull

@Throws(IOException::class)
fun <T> JsonReader.nextOrNull(next: () -> T): T? {
    return when (peek()) {
        JsonToken.NULL -> null
        else -> next()
    }
}

@Throws(IOException::class)
fun JsonReader.nextStringOrNull(): String? =
    nextOrNull { nextString() }

@Throws(IOException::class)
fun JsonReader.nextDoubleOrNull(): Double? =
    nextOrNull { nextDouble() }

@Throws(IOException::class)
fun JsonReader.nextLongOrNull(): Long? =
    nextOrNull { nextLong() }

@Throws(IOException::class)
fun JsonReader.nextIntOrNull(): Int? =
    nextOrNull { nextInt() }

@Throws(IOException::class)
fun JsonReader.nextBooleanOrNull(): Boolean? =
    nextOrNull { nextBoolean() }


