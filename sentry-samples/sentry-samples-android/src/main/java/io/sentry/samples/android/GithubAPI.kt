package io.sentry.samples.android

import io.sentry.HttpStatusCodeRange
import io.sentry.okhttp.SentryOkHttpEventListener
import io.sentry.okhttp.SentryOkHttpInterceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object GithubAPI {
  private val client =
    OkHttpClient.Builder()
      .eventListener(SentryOkHttpEventListener())
      .addInterceptor(
        SentryOkHttpInterceptor(
          captureFailedRequests = true,
          failedRequestStatusCodes = listOf(HttpStatusCodeRange(400, 599)),
        )
      )
      .build()

  private val retrofit =
    Retrofit.Builder()
      .baseUrl("https://api.github.com/")
      .addConverterFactory(GsonConverterFactory.create())
      .client(client)
      .build()

  val service: GitHubService = retrofit.create(GitHubService::class.java)
}
