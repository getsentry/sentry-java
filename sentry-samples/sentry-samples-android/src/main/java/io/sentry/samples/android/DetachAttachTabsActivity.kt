package io.sentry.samples.android

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit

class DetachAttachTabsActivity : AppCompatActivity(R.layout.activity_detach_attach_tabs) {

  private val tags = arrayOf("tab_a", "tab_b")

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    findViewById<View>(R.id.btn_tab_a).setOnClickListener { showTab(0) }
    findViewById<View>(R.id.btn_tab_b).setOnClickListener { showTab(1) }

    if (savedInstanceState == null) {
      val tabB = TabFragmentB()
      supportFragmentManager.commit {
        add(R.id.tab_container, TabFragmentA(), tags[0])
        add(R.id.tab_container, tabB, tags[1])
        detach(tabB)
      }
    }
  }

  private fun showTab(index: Int) {
    supportFragmentManager.commit {
      for (i in tags.indices) {
        val frag = supportFragmentManager.findFragmentByTag(tags[i]) ?: continue
        if (i == index) attach(frag) else detach(frag)
      }
    }
  }
}

class TabFragmentA : Fragment(R.layout.fragment_tab) {
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    view.findViewById<TextView>(R.id.tab_label).text = "Tab A"
  }
}

class TabFragmentB : Fragment(R.layout.fragment_tab) {
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    view.findViewById<TextView>(R.id.tab_label).text = "Tab B"
  }
}
