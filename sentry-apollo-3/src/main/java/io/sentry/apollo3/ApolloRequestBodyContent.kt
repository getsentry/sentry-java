package io.sentry.apollo3

import io.sentry.ILogger
import io.sentry.JsonDeserializer
import io.sentry.JsonObjectReader
import io.sentry.vendor.gson.stream.JsonToken

data class ApolloRequestBodyListContent(
    var queries: List<ApolloRequestBodyContent>?
) {
    object Deserializer : JsonDeserializer<ApolloRequestBodyListContent> {
        override fun deserialize(
            reader: JsonObjectReader,
            logger: ILogger
        ): ApolloRequestBodyListContent {
            reader.beginArray()
            val requestBodyListContent = ApolloRequestBodyListContent(
                reader.nextList(
                    logger,
                    ApolloRequestBodyContent.Deserializer
                )
            )
            reader.endArray()

            return requestBodyListContent
        }
    }
}

data class ApolloRequestBodyContent(
    var query: String = "",
    var variables: Map<String, Any>? = null
) {
    // JsonSerializable
    object JsonKeys {
        const val QUERY = "query"
        const val VARIABLES = "variables"
    }

    object Deserializer : JsonDeserializer<ApolloRequestBodyContent> {
        override fun deserialize(reader: JsonObjectReader, logger: ILogger): ApolloRequestBodyContent {
            val content = ApolloRequestBodyContent()
            reader.beginObject()
            while (reader.peek() == JsonToken.NAME) {
                when (val nextName = reader.nextName()) {
                    JsonKeys.VARIABLES -> content.variables = reader.nextObjectOrNull() as? Map<String, Any>
                    JsonKeys.QUERY -> content.query = reader.nextString()
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
