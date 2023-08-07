package io.sentry.samples.android.graphql

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.api.Optional
import com.apollographql.apollo3.network.okHttpClient
import io.sentry.Sentry
import io.sentry.SpanStatus
import io.sentry.apollo3.sentryTracing
import io.sentry.samples.android.MainActivity
import io.sentry.samples.android.databinding.ActivityGraphqlBinding
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Response

class GraphqlActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGraphqlBinding
    private lateinit var apollo: ApolloClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        apollo = ApolloClient.Builder()
            .serverUrl("http://10.0.2.2:8080/graphql")
            .okHttpClient(
                OkHttpClient.Builder()
                    .addInterceptor(AuthorizationInterceptor()).build()
            )
            .sentryTracing(captureFailedRequests = true)
            .build()

        val activeSpan = Sentry.getSpan()
        val span = activeSpan?.startChild("onCreate", javaClass.simpleName)

        binding = ActivityGraphqlBinding.inflate(layoutInflater)

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

    private fun showText(visible: Boolean = true, text: String = "") {
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
        val span = currentSpan?.startChild("updateRepos", javaClass.simpleName)
            ?: Sentry.startTransaction("updateRepos", "task")

        runBlocking {
            val response = apollo.query(GreetingQuery(Optional.Present("crash"))).execute()
            span.finish()
            showText(visible = true, response.data.toString())
        }
    }
}

class AuthorizationInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val request = chain.request().newBuilder()
            .apply { addHeader("Authorization", "Basic dXNlcjpwYXNzd29yZA==") }
            .build()
        return chain.proceed(request)
    }
}
