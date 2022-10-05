package io.sentry.samples.android

import io.sentry.android.okhttp.SentryOkHttpInterceptor
import io.sentry.android.okhttp.StatusCodeRange
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object GithubAPI {

    private val client = OkHttpClient.Builder().addInterceptor(SentryOkHttpInterceptor(captureFailedRequests = true, failedRequestStatusCodes = listOf(
        StatusCodeRange(200, 599)
    ))).build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.github.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .client(client)
        .build()

    val service: GitHubService = retrofit.create(GitHubService::class.java)
}
