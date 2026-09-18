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

  fun enqueueRouteWorkRequest(callback: retrofit2.Callback<okhttp3.ResponseBody>) {
    service.routeWorkRequest(HTTPBIN_ROUTE_WORK_URL).enqueue(callback)
  }

  suspend fun runRouteWorkRequest() {
    service.routeWorkRequestAsync(HTTPBIN_ROUTE_WORK_URL)
  }
}

private const val HTTPBIN_ROUTE_WORK_URL =
  "https://httpbin.org/get?sentry_sample=navigation_route_work"
