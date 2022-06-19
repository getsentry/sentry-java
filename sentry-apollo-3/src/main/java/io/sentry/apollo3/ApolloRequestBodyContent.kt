package io.sentry.apollo3

import io.sentry.ILogger
import io.sentry.JsonDeserializer
import io.sentry.JsonObjectReader
import io.sentry.vendor.gson.stream.JsonToken

data class ApolloRequestBodyContent(
    var query: String = "",
    var variables: Map<String, Any>? = null,
    var operationName: String? = null
) {
    // JsonSerializable
    object JsonKeys {
        const val QUERY = "query"
        const val VARIABLES = "variables"
        const val OPERATION_NAME = "operationName"
    }

    object Deserializer : JsonDeserializer<ApolloRequestBodyContent> {
        override fun deserialize(reader: JsonObjectReader, logger: ILogger): ApolloRequestBodyContent {
            val content = ApolloRequestBodyContent()
            reader.beginObject()
            while (reader.peek() == JsonToken.NAME) {
                when (val nextName = reader.nextName()) {
                    JsonKeys.VARIABLES -> content.variables = reader.nextObjectOrNull() as? Map<String, Any>
                    JsonKeys.QUERY -> content.query = reader.nextString()
                    JsonKeys.OPERATION_NAME -> content.operationName = reader.nextString()
                    else -> {
                        reader.nextUnknown(logger, mutableMapOf(), nextName)
                    }
                }
            }
            reader.endObject()
            return content
        }
    }
}
