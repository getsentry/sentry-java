package io.sentry.apollo3

import com.apollographql.apollo3.api.ApolloRequest
import com.apollographql.apollo3.api.ApolloResponse
import com.apollographql.apollo3.api.CustomScalarAdapters
import com.apollographql.apollo3.api.Operation
import com.apollographql.apollo3.api.http.HttpResponse
import com.apollographql.apollo3.network.http.HttpInfo

val <D : Operation.Data> ApolloResponse<D>.httpInfo
    get() = executionContext[HttpInfo]

val <D : Operation.Data> ApolloRequest<D>.scalarAdapters
    get() = executionContext[CustomScalarAdapters]

fun HttpResponse.headersContentLength(): Long {
    return headers.firstOrNull { it.name == "Content-Length" }?.value?.toLongOrDefault(-1L) ?: -1L
}

fun String.toLongOrDefault(defaultValue: Long): Long {
    return try {
        toLong()
    } catch (_: NumberFormatException) {
        defaultValue
    }
}
