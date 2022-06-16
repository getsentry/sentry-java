package io.sentry.apollo3

import com.apollographql.apollo3.api.http.HttpResponse

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
