package io.sentry.samples.android

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import io.sentry.Sentry
import io.sentry.SpanStatus

class ThirdFragment : Fragment(R.layout.third_fragment) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val someInt = requireArguments().getInt("some_int")

        val span = Sentry.getSpan()
        val child = span?.startChild("calc")

        for (i in 0..someInt) {
            println(i)
        }
        child?.finish(SpanStatus.OK)
    }
}
