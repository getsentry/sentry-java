package io.sentry.samples.android.navigation.common

import io.sentry.HttpStatusCodeRange
import io.sentry.okhttp.SentryOkHttpEventListener
import io.sentry.okhttp.SentryOkHttpInterceptor
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Url

internal object RouteWorkApi {
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
      .baseUrl("https://httpbin.org/")
      .addConverterFactory(GsonConverterFactory.create())
      .client(client)
      .build()

  private val service: RouteWorkService = retrofit.create(RouteWorkService::class.java)

  fun enqueueRequest(onSuccess: () -> Unit, onFailure: (Throwable) -> Unit) {
    service
      .request(ROUTE_WORK_URL)
      .enqueue(
        object : retrofit2.Callback<ResponseBody> {
          override fun onResponse(
            call: retrofit2.Call<ResponseBody>,
            response: retrofit2.Response<ResponseBody>,
          ) {
            response.body()?.close()
            response.errorBody()?.close()
            onSuccess()
          }

          override fun onFailure(call: retrofit2.Call<ResponseBody>, t: Throwable) {
            onFailure(t)
          }
        }
      )
  }

  suspend fun runRequest() {
    service.requestAsync(ROUTE_WORK_URL).use {}
  }
}

internal interface RouteWorkService {
  @GET fun request(@Url url: String): Call<ResponseBody>

  @GET suspend fun requestAsync(@Url url: String): ResponseBody
}

private const val ROUTE_WORK_URL = "https://httpbin.org/get?sentry_sample=navigation_route_work"
