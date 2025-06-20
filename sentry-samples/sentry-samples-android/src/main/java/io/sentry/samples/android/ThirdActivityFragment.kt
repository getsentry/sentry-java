package io.sentry.samples.android

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.fragment.app.add
import androidx.fragment.app.commit

class ThirdActivityFragment : AppCompatActivity(R.layout.activity_third_fragment) {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    if (savedInstanceState == null) {
      val bundle = bundleOf("some_int" to 1000)
      supportFragmentManager.commit {
        setReorderingAllowed(true)
        add<ThirdFragment>(R.id.fragment_container_view, args = bundle)
      }
    }
  }
}
