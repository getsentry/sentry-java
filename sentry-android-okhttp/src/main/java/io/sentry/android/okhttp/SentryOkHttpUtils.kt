package io.sentry.android.okhttp

import io.sentry.IHub
import okhttp3.Request
import okhttp3.Response

@Deprecated(
    "Use SentryOkHttpUtils from sentry-okhttp instead",
    ReplaceWith("SentryOkHttpUtils", "io.sentry.okhttp.SentryOkHttpUtils")
)
object SentryOkHttpUtils {

    fun captureClientError(hub: IHub, request: Request, response: Response) {
        io.sentry.okhttp.SentryOkHttpUtils.captureClientError(hub, request, response)
    }
}
