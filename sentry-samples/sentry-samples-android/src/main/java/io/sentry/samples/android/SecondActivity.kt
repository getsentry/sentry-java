package io.sentry.samples.android

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import io.sentry.Sentry
import io.sentry.SpanStatus
import io.sentry.samples.android.databinding.ActivitySecondBinding
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class SecondActivity : AppCompatActivity() {

    private lateinit var repos: List<Repo>

    private val client = OkHttpClient.Builder().addInterceptor(NetworkInterceptor()).build()

    private lateinit var binding: ActivitySecondBinding

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

        binding = ActivitySecondBinding.inflate(layoutInflater)

        binding.doRequest.setOnClickListener {
            // this wont create a transaction because the one in the scope is already finished,
            // unless you opt-out enableAutoActivityLifecycleTracingFinish
            updateRepos()
//            finish()
//            startActivity(Intent(this, MainActivity::class.java))
        }

        // do some stuff

        setContentView(binding.root)

        span?.finish(SpanStatus.OK)
    }

    private fun showText(visible: Boolean = true) {
        binding.text.text = if (visible) "items: ${repos.size}" else ""
        binding.text.visibility = if (visible) View.VISIBLE else View.GONE

        binding.indeterminateBar.visibility = if (visible) View.GONE else View.VISIBLE
    }

    override fun onResume() {
        super.onResume()

        val span = Sentry.getSpan()?.startChild("onResume", javaClass.simpleName)

        // do some stuff

        updateRepos(true)

        // do some stuff

        span?.finish(SpanStatus.OK)
    }

    private fun updateRepos(finishTransaction: Boolean = false) {
        val span = Sentry.getSpan()?.startChild("updateRepos", javaClass.simpleName)

        service.listRepos("getsentry").enqueue(object : Callback<List<Repo>> {
            override fun onFailure(call: Call<List<Repo>>?, t: Throwable) {
                span?.finish(SpanStatus.INTERNAL_ERROR)
                Sentry.captureException(t)

                showText(false)

                finishTransaction(finishTransaction, SpanStatus.INTERNAL_ERROR)
            }

            override fun onResponse(call: Call<List<Repo>>, response: Response<List<Repo>>) {
                repos = response.body() ?: emptyList()

                span?.finish(SpanStatus.OK)

                showText()

                // I opt out enableAutoActivityLifecycleTracingFinish so I when best when to end my transaction
                finishTransaction(finishTransaction)
            }
        })
    }

    private fun finishTransaction(finishTransaction: Boolean, status: SpanStatus = SpanStatus.OK) {
        if (!finishTransaction) return
        Sentry.configureScope {
            it.transaction?.finish(status)
        }
    }
}
