package io.sentry.samples.android

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import io.sentry.Sentry
import io.sentry.samples.android.databinding.FragmentSampleInnerBinding

class SampleInnerFragment : Fragment() {
  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
  ): View =
    FragmentSampleInnerBinding.inflate(inflater)
      .apply {
        this.sendMessage.setOnClickListener {
          Sentry.captureMessage("Some message from inner Fragment Lifecycle events in breadcrumbs.")
        }
      }
      .root

  companion object {
    @JvmStatic fun newInstance() = SampleInnerFragment()
  }
}
