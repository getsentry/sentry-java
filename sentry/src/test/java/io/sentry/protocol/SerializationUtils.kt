package io.sentry.protocol

import io.sentry.FileFromResources
import io.sentry.ILogger
import io.sentry.JsonDeserializer
import io.sentry.JsonObjectReader
import io.sentry.JsonObjectWriter
import io.sentry.JsonSerializable
import java.io.StringReader
import java.io.StringWriter

object SerializationUtils {
    // TODO: refactor every ser and deser tests to reuse this utils, a lot of boilerplate

    fun <T> deserializeJson(
        json: String,
        deserializer: JsonDeserializer<T>,
        logger: ILogger,
    ): T {
        val reader = JsonObjectReader(StringReader(json))
        return deserializer.deserialize(reader, logger)
    }

    fun sanitizedFile(path: String): String =
        FileFromResources
            .invoke(path)
            .replace(Regex("[\n\r]"), "")
            .replace(" ", "")

    fun serializeToString(
        jsonSerializable: JsonSerializable,
        logger: ILogger,
    ): String = this.serializeToString { wrt -> jsonSerializable.serialize(wrt, logger) }

    private fun serializeToString(serialize: (JsonObjectWriter) -> Unit): String {
        val wrt = StringWriter()
        val jsonWrt = JsonObjectWriter(wrt, 100)
        serialize(jsonWrt)
        return wrt.toString()
    }
}
