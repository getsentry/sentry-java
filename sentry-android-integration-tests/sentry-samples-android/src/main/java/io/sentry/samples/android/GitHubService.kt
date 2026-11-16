package io.sentry.samples.android

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

interface GitHubService {

  @GET("users/{user}/repos") fun listRepos(@Path("user") user: String): Call<List<Repo>>

  @GET("users/{user}/repos")
  suspend fun listReposAsync(
    @Path("user") user: String,
    @Query("per_page") perPage: Int,
  ): List<Repo>

  @GET fun routeWorkRequest(@Url url: String): Call<ResponseBody>

  @GET suspend fun routeWorkRequestAsync(@Url url: String): ResponseBody
}

class Repo {
  val full_name: String = ""
}
