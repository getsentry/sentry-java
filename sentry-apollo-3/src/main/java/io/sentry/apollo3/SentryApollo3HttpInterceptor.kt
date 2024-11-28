package io.sentry.apollo3

import com.apollographql.apollo3.api.http.DefaultHttpRequestComposer.Companion.HEADER_APOLLO_OPERATION_ID
import com.apollographql.apollo3.api.http.DefaultHttpRequestComposer.Companion.HEADER_APOLLO_OPERATION_NAME
import com.apollographql.apollo3.api.http.HttpHeader
import com.apollographql.apollo3.api.http.HttpRequest
import com.apollographql.apollo3.api.http.HttpResponse
import com.apollographql.apollo3.exception.ApolloHttpException
import com.apollographql.apollo3.network.http.HttpInterceptor
import com.apollographql.apollo3.network.http.HttpInterceptorChain
import io.sentry.BaggageHeader
import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.IScopes
import io.sentry.ISpan
import io.sentry.ScopesAdapter
import io.sentry.SentryEvent
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.SentryLevel
import io.sentry.SentryOptions.DEFAULT_PROPAGATION_TARGETS
import io.sentry.SpanDataConvention
import io.sentry.SpanDataConvention.HTTP_METHOD_KEY
import io.sentry.SpanStatus
import io.sentry.TypeCheckHint.APOLLO_REQUEST
import io.sentry.TypeCheckHint.APOLLO_RESPONSE
import io.sentry.exception.ExceptionMechanismException
import io.sentry.protocol.Mechanism
import io.sentry.protocol.Request
import io.sentry.protocol.Response
import io.sentry.util.HttpUtils
import io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion
import io.sentry.util.Platform
import io.sentry.util.PropagationTargetsUtils
import io.sentry.util.TracingUtils
import io.sentry.util.UrlUtils
import io.sentry.vendor.Base64
import okio.Buffer
import org.jetbrains.annotations.ApiStatus

private const val TRACE_ORIGIN = "auto.graphql.apollo3"

class SentryApollo3HttpInterceptor @JvmOverloads constructor(
    @ApiStatus.Internal private val scopes: IScopes = ScopesAdapter.getInstance(),
    private val beforeSpan: BeforeSpanCallback? = null,
    private val captureFailedRequests: Boolean = DEFAULT_CAPTURE_FAILED_REQUESTS,
    private val failedRequestTargets: List<String> = listOf(DEFAULT_PROPAGATION_TARGETS)
) : HttpInterceptor {

    init {
        addIntegrationToSdkVersion("Apollo3")
        if (captureFailedRequests) {
            SentryIntegrationPackageStorage.getInstance()
                .addIntegration("Apollo3ClientError")
        }
        SentryIntegrationPackageStorage.getInstance()
            .addPackage("maven:io.sentry:sentry-apollo-3", BuildConfig.VERSION_NAME)
    }

    private val regex: Regex by lazy {
        "(?i)\"errors\"\\s*:\\s*\\[".toRegex()
    }

    override suspend fun intercept(
        request: HttpRequest,
        chain: HttpInterceptorChain
    ): HttpResponse {
        val activeSpan = if (Platform.isAndroid()) scopes.transaction else scopes.span

        val operationName = getHeader(HEADER_APOLLO_OPERATION_NAME, request.headers)
        val operationType = decodeHeaderValue(request, SENTRY_APOLLO_3_OPERATION_TYPE)
        val operationId = getHeader(HEADER_APOLLO_OPERATION_ID, request.headers)

        var span: ISpan? = null

        if (activeSpan != null) {
            span = startChild(request, activeSpan, operationName, operationType, operationId)
        }

        val modifiedRequest = maybeAddTracingHeaders(scopes, request, span)
        var httpResponse: HttpResponse? = null
        var statusCode: Int? = null

        try {
            httpResponse = chain.proceed(modifiedRequest)
            statusCode = httpResponse.statusCode
            span?.setData(SpanDataConvention.HTTP_STATUS_CODE_KEY, statusCode)
            span?.status = SpanStatus.fromHttpStatusCode(statusCode)

            captureEvent(modifiedRequest, httpResponse, operationName, operationType)

            return httpResponse
        } catch (e: Throwable) {
            // https://github.com/apollographql/apollo-kotlin/issues/4711 will change error handling in v4
            when (e) {
                is ApolloHttpException -> {
                    statusCode = e.statusCode
                    span?.setData(SpanDataConvention.HTTP_STATUS_CODE_KEY, statusCode)
                    span?.status =
                        SpanStatus.fromHttpStatusCode(statusCode, SpanStatus.INTERNAL_ERROR)
                }

                else -> span?.status = SpanStatus.INTERNAL_ERROR
            }
            span?.throwable = e
            throw e
        } finally {
            finish(
                span,
                modifiedRequest,
                httpResponse,
                statusCode,
                operationName,
                operationType,
                operationId
            )
        }
    }

    private fun maybeAddTracingHeaders(scopes: IScopes, request: HttpRequest, span: ISpan?): HttpRequest {
        var cleanedHeaders = removeSentryInternalHeaders(request.headers).toMutableList()

        TracingUtils.traceIfAllowed(scopes, request.url, request.headers.filter { it.name == BaggageHeader.BAGGAGE_HEADER }.map { it.value }, span)?.let {
            cleanedHeaders.add(HttpHeader(it.sentryTraceHeader.name, it.sentryTraceHeader.value))
            it.baggageHeader?.let { baggageHeader ->
                cleanedHeaders = cleanedHeaders.filterNot { it.name == BaggageHeader.BAGGAGE_HEADER }.toMutableList().apply {
                    add(HttpHeader(baggageHeader.name, baggageHeader.value))
                }
            }
        }

        val requestBuilder = request.newBuilder().apply {
            headers(cleanedHeaders)
        }

        return requestBuilder.build()
    }

    private fun removeSentryInternalHeaders(headers: List<HttpHeader>): List<HttpHeader> {
        return headers.filterNot {
            it.name.equals(SENTRY_APOLLO_3_VARIABLES, true) ||
                it.name.equals(SENTRY_APOLLO_3_OPERATION_TYPE, true)
        }
    }

    private fun startChild(
        request: HttpRequest,
        activeSpan: ISpan,
        operationName: String?,
        operationType: String?,
        operationId: String?
    ): ISpan {
        val urlDetails = UrlUtils.parse(request.url)
        val method = request.method.name

        val operation = if (operationType != null) "http.graphql.$operationType" else "http.graphql"
        val variables = decodeHeaderValue(request, SENTRY_APOLLO_3_VARIABLES)

        val description = "${operationType ?: method} ${operationName ?: urlDetails.urlOrFallback}"

        return activeSpan.startChild(operation, description).apply {
            urlDetails.applyToSpan(this)

            spanContext.origin = TRACE_ORIGIN

            operationId?.let {
                setData("operationId", it)
            }

            variables?.let {
                setData("variables", it)
            }
            setData(HTTP_METHOD_KEY, method.uppercase())
        }
    }

    private fun decodeHeaderValue(request: HttpRequest, headerName: String): String? {
        return getHeader(headerName, request.headers)?.let {
            try {
                String(Base64.decode(it, Base64.NO_WRAP))
            } catch (e: Throwable) {
                scopes.options.logger.log(
                    SentryLevel.ERROR,
                    "Error decoding internal apolloHeader $headerName",
                    e
                )
                return null
            }
        }
    }

    private fun finish(
        span: ISpan?,
        request: HttpRequest,
        response: HttpResponse?,
        statusCode: Int?,
        operationName: String?,
        operationType: String?,
        operationId: String?
    ) {
        var responseContentLength: Long? = null
        response?.body?.buffer?.size?.ifHasValidLength {
            responseContentLength = it
        }

        if (span != null) {
            statusCode?.let {
                span.setData(SpanDataConvention.HTTP_STATUS_CODE_KEY, statusCode)
            }
            responseContentLength?.let {
                span.setData(SpanDataConvention.HTTP_RESPONSE_CONTENT_LENGTH_KEY, it)
            }
            if (beforeSpan != null) {
                try {
                    val result = beforeSpan.execute(span, request, response)
                    if (result == null) {
                        // Span is dropped
                        span.spanContext.sampled = false
                    }
                } catch (e: Throwable) {
                    scopes.options.logger.log(
                        SentryLevel.ERROR,
                        "An error occurred while executing beforeSpan on ApolloInterceptor",
                        e
                    )
                }
            }
            span.finish()
        }

        val breadcrumb = Breadcrumb.http(request.url, request.method.name, statusCode)

        request.body?.contentLength.ifHasValidLength { contentLength ->
            breadcrumb.setData("request_body_size", contentLength)
        }

        operationName?.let {
            breadcrumb.setData("operation_name", it)
        }
        operationType?.let {
            breadcrumb.setData("operation_type", it)
        }
        operationId?.let {
            breadcrumb.setData("operation_id", it)
        }

        val hint = Hint().also {
            it.set(APOLLO_REQUEST, request)
        }

        response?.let { httpResponse ->
            responseContentLength?.let {
                breadcrumb.setData("response_body_size", it)
            }

            hint.set(APOLLO_RESPONSE, httpResponse)
        }

        scopes.addBreadcrumb(breadcrumb, hint)
    }

    // Extensions

    private fun Long?.ifHasValidLength(fn: (Long) -> Unit) {
        if (this != null && this != -1L) {
            fn.invoke(this)
        }
    }

    private fun getHeader(key: String, headers: List<HttpHeader>): String? {
        return headers.firstOrNull { it.name.equals(key, true) }?.value
    }

    private fun getHeaders(headers: List<HttpHeader>): MutableMap<String, String>? {
        // Headers are only sent if isSendDefaultPii is enabled due to PII
        if (!scopes.options.isSendDefaultPii) {
            return null
        }

        val headersMap = mutableMapOf<String, String>()

        for (item in headers) {
            val name = item.name

            // header is only sent if isn't sensitive
            if (HttpUtils.containsSensitiveHeader(name)) {
                continue
            }

            headersMap[name] = item.value
        }
        return headersMap.ifEmpty { null }
    }

    private fun captureEvent(
        request: HttpRequest,
        response: HttpResponse,
        operationName: String?,
        operationType: String?
    ) {
        // return if the feature is disabled
        if (!captureFailedRequests) {
            return
        }

        // wrap everything up in a try catch block so every exception is swallowed and degraded
        // gracefully
        try {
            // we pay the price to read the response in the memory to check if there's any errors
            // GraphQL does not throw status code 400+ for every type of error
            val body = try {
                response.body?.peek()?.readUtf8() ?: ""
            } catch (e: Throwable) {
                scopes.options.logger.log(
                    SentryLevel.ERROR,
                    "Error reading the response body.",
                    e
                )
                // bail out because the response body has the most important information
                return
            }

            // if there response body does not have the errors field, do not raise an issue
            if (body.isEmpty() || !regex.containsMatchIn(body)) {
                return
            }

            // not possible to get a parameterized url, but we remove at least the
            // query string and the fragment.
            // url example: https://api.github.com/users/getsentry/repos/#fragment?query=query
            // url will be: https://api.github.com/users/getsentry/repos/
            // ideally we'd like a parameterized url: https://api.github.com/users/{user}/repos/
            // but that's not possible
            val urlDetails = UrlUtils.parse(request.url)

            // return if its not a target match
            if (!PropagationTargetsUtils.contain(failedRequestTargets, urlDetails.urlOrFallback)) {
                return
            }

            val mechanism = Mechanism().apply {
                type = "SentryApollo3Interceptor"
            }

            val fingerprints = mutableListOf<String>()

            val builder = StringBuilder()
            builder.append("GraphQL Request failed")
            operationName?.let {
                builder.append(", name: $it")
                fingerprints.add(operationName)
            }
            operationType?.let {
                builder.append(", type: $it")
                fingerprints.add(operationType)
            }

            val exception = SentryApollo3ClientException(builder.toString())
            val mechanismException =
                ExceptionMechanismException(mechanism, exception, Thread.currentThread(), true)
            val event = SentryEvent(mechanismException)

            val hint = Hint()
            hint.set(APOLLO_REQUEST, request)
            hint.set(APOLLO_RESPONSE, response)

            val sentryRequest = Request().apply {
                urlDetails.applyToRequest(this)
                // Cookie is only sent if isSendDefaultPii is enabled
                cookies =
                    if (scopes.options.isSendDefaultPii) getHeader("Cookie", request.headers) else null
                method = request.method.name
                headers = getHeaders(request.headers)
                apiTarget = "graphql"

                request.body?.let {
                    bodySize = it.contentLength

                    val buffer = Buffer()

                    try {
                        it.writeTo(buffer)
                        data = buffer.readUtf8()
                    } catch (e: Throwable) {
                        scopes.options.logger.log(
                            SentryLevel.ERROR,
                            "Error reading the request body.",
                            e
                        )
                        // continue because the response body alone can already give some insights
                    } finally {
                        buffer.close()
                    }
                }
            }

            val sentryResponse = Response().apply {
                // Set-Cookie is only sent if isSendDefaultPii is enabled due to PII
                cookies = if (scopes.options.isSendDefaultPii) {
                    getHeader(
                        "Set-Cookie",
                        response.headers
                    )
                } else {
                    null
                }
                headers = getHeaders(response.headers)
                statusCode = response.statusCode

                response.body?.buffer?.size?.ifHasValidLength { contentLength ->
                    bodySize = contentLength
                }
                data = body
            }

            fingerprints.add(response.statusCode.toString())

            event.request = sentryRequest
            event.contexts.setResponse(sentryResponse)
            event.fingerprints = fingerprints

            scopes.captureEvent(event, hint)
        } catch (e: Throwable) {
            scopes.options.logger.log(
                SentryLevel.ERROR,
                "Error capturing the GraphQL error.",
                e
            )
        }
    }

    /**
     * The BeforeSpan callback
     */
    fun interface BeforeSpanCallback {
        /**
         * Mutates span before being added.
         *
         * @param span the span to mutate or drop
         * @param request the Apollo request object
         * @param response the Apollo response object
         */
        fun execute(span: ISpan, request: HttpRequest, response: HttpResponse?): ISpan?
    }

    companion object {
        const val SENTRY_APOLLO_3_VARIABLES = "SENTRY-APOLLO-3-VARIABLES"
        const val SENTRY_APOLLO_3_OPERATION_TYPE = "SENTRY-APOLLO-3-OPERATION-TYPE"
        const val DEFAULT_CAPTURE_FAILED_REQUESTS = true
    }
}
