package io.sentry.samples.android

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface GitHubService {

  @GET("users/{user}/repos") fun listRepos(@Path("user") user: String): Call<List<Repo>>

  @GET("users/{user}/repos")
  suspend fun listReposAsync(
    @Path("user") user: String,
    @Query("per_page") perPage: Int,
  ): List<Repo>
}

class Repo {
  val full_name: String = ""
}
