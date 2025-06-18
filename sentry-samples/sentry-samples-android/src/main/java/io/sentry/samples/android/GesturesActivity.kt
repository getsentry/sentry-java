package io.sentry.samples.android

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import io.sentry.Sentry
import io.sentry.samples.android.R.layout
import io.sentry.samples.android.databinding.ActivityGesturesBinding
import io.sentry.samples.android.databinding.FragmentRecyclerBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.abs

@Suppress("DEPRECATION")
class GesturesActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGesturesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityGesturesBinding.inflate(layoutInflater)

        binding.pager.adapter =
            object : FragmentStatePagerAdapter(supportFragmentManager) {
                override fun getCount(): Int = 2

                override fun getItem(position: Int): Fragment = if (position == 0) ScrollingFragment() else RecyclerFragment()
            }

        binding.scrollingCrash.setOnClickListener {
            throw RuntimeException("Uncaught Exception")
        }

        setContentView(binding.root)
    }

    override fun onResume() {
        super.onResume()
        Sentry.getSpan()?.finish()
    }
}

class ScrollingFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(layout.fragment_scrolling, container, false)

    @SuppressLint("NewApi")
    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<ScrollView>(R.id.scrolling_container).setOnScrollChangeListener { v, scrollX, scrollY, oldScrollX, oldScrollY ->
            if (abs(oldScrollY - scrollY) > 100) {
                val child = Sentry.getSpan()?.startChild("load_more")
                lifecycleScope.launch {
                    delay(1000)
                    child?.finish()
                }
            }
        }
    }
}

class RecyclerFragment : Fragment() {
    private var binding: FragmentRecyclerBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        this.binding = FragmentRecyclerBinding.inflate(inflater, container, false)
        val binding = requireNotNull(this.binding)
        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        binding.recyclerView.adapter = RecyclerAdapter()
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private class RecyclerAdapter : RecyclerView.Adapter<ViewHolder>() {
        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int,
        ): ViewHolder {
            val view =
                LayoutInflater
                    .from(parent.context)
                    .inflate(android.R.layout.simple_list_item_1, parent, false)
            return object : ViewHolder(view) {}
        }

        override fun onBindViewHolder(
            holder: ViewHolder,
            position: Int,
        ) {
            (holder.itemView as TextView).text = UUID.randomUUID().toString()
        }

        override fun getItemCount(): Int = 100
    }
}
