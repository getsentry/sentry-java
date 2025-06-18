package io.sentry.samples.android

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import io.sentry.samples.android.databinding.FragmentSampleBinding

class SampleFragment : DialogFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View =
        FragmentSampleBinding
            .inflate(inflater)
            .apply {
                childFragmentManager
                    .beginTransaction()
                    .replace(R.id.container, SampleInnerFragment.newInstance())
                    .commit()
            }.root

    companion object {
        @JvmStatic fun newInstance() = SampleFragment()
    }
}
