package io.sentry.samples.android

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import io.sentry.Sentry
import io.sentry.SpanStatus
import io.sentry.samples.android.databinding.ActivitySecondBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class SecondActivity : AppCompatActivity() {
    private lateinit var repos: List<Repo>

    private lateinit var binding: ActivitySecondBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val activeSpan = Sentry.getSpan()
        val span = activeSpan?.startChild("onCreate", javaClass.simpleName)

        binding = ActivitySecondBinding.inflate(layoutInflater)

        binding.doRequest.setOnClickListener {
            updateRepos()
        }

        binding.backMain.setOnClickListener {
            finish()
            startActivity(Intent(this, MainActivity::class.java))
        }

        // do some stuff

        setContentView(binding.root)

        span?.finish(SpanStatus.OK)
    }

    private fun showText(
        visible: Boolean = true,
        text: String = "",
    ) {
        binding.text.text = if (visible) text else ""
        binding.text.visibility = if (visible) View.VISIBLE else View.GONE

        binding.indeterminateBar.visibility = if (visible) View.GONE else View.VISIBLE
    }

    override fun onResume() {
        super.onResume()

        val span = Sentry.getSpan()?.startChild("onResume", javaClass.simpleName)

        // do some stuff

        updateRepos()

        // do some stuff

        span?.finish(SpanStatus.OK)
    }

    private fun updateRepos() {
        showText(false)

        val currentSpan = Sentry.getSpan()
        val span =
            currentSpan?.startChild("updateRepos", javaClass.simpleName)
                ?: Sentry.startTransaction("updateRepos", "task")

        GithubAPI.service.listRepos(binding.editRepo.text.toString()).enqueue(
            object : Callback<List<Repo>> {
                override fun onFailure(
                    call: Call<List<Repo>>,
                    t: Throwable,
                ) {
                    span.finish(SpanStatus.INTERNAL_ERROR)
                    Sentry.captureException(t)

                    showText(true, "error: ${t.message}")

                    Sentry.reportFullyDisplayed()
                }

                override fun onResponse(
                    call: Call<List<Repo>>,
                    response: Response<List<Repo>>,
                ) {
                    repos = response.body() ?: emptyList()

                    span.finish(SpanStatus.OK)

                    showText(text = "items: ${repos.size}")

                    Sentry.reportFullyDisplayed()
                }
            },
        )
    }
}
