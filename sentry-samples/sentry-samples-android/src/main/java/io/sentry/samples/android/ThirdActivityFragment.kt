package io.sentry.samples.android

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.fragment.app.add
import androidx.fragment.app.commit
import io.sentry.Sentry
import io.sentry.SpanStatus
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ThirdActivityFragment : AppCompatActivity(R.layout.activity_third_fragment) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            val bundle = bundleOf("some_int" to 0)
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                add<ThirdFragment>(R.id.fragment_container_view, args = bundle)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // this is called before fragments are fully rendered, as they have their own lifecycle
        // activity is only responsible of rendering the containers for fragments (FragmentContainerView)
        // so I cant finish my transaction here manually.
        // we'd need idle transactions here, I will simulate with a delayed coroutines

        GlobalScope.launch {
            delay(1500L)
            val span = Sentry.getSpan()
            span?.finish(SpanStatus.OK)
        }
    }
}
