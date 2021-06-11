package io.sentry.samples.android

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment

class ThirdFragment : Fragment(R.layout.third_fragment) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val someInt = requireArguments().getInt("some_int")
    }
}
