package io.sentry.samples.android

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.sentry.Sentry
import io.sentry.SpanStatus
import io.sentry.samples.android.databinding.ActivitySecondBinding
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class SecondActivity : AppCompatActivity() {

    private lateinit var repos: List<Repo>

    private val client = OkHttpClient.Builder().addInterceptor(NetworkInterceptor()).build()

    private val retrofit = Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()

    private val service = retrofit.create(GitHubService::class.java)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val activeSpan = Sentry.getSpan()
        val span = activeSpan?.startChild("onCreate", javaClass.simpleName)

        val binding = ActivitySecondBinding.inflate(layoutInflater)

        binding.doRequest.setOnClickListener {
            // this wont create a transaction because the one in the scope is already finished
            updateRepos()
        }

        // do some stuff

        setContentView(binding.root)

        span?.finish(SpanStatus.OK)
    }

    override fun onStart() {
        super.onStart()

        val span = Sentry.getSpan()?.startChild("onStart", javaClass.simpleName)

        // do some stuff

        updateRepos()

        // do some stuff

        span?.finish(SpanStatus.OK)
    }

    private fun updateRepos() {
        val span = Sentry.getSpan()?.startChild("updateRepos", javaClass.simpleName)

        var status = SpanStatus.OK
        try {
            val response = service.listRepos("getsentry").execute()
            repos = response.body() ?: emptyList()

            println("print: ${repos.size}")
        } catch (e: Exception) {
            Sentry.captureException(e)
            status = SpanStatus.INTERNAL_ERROR
        }

        span?.finish(status)
    }
}