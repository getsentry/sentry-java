package io.sentry.uitest.android

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.idling.CountingIdlingResource
import io.sentry.uitest.android.databinding.ActivityProfilingSampleBinding
import io.sentry.uitest.android.databinding.ProfilingSampleItemListBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.random.Random

class ProfilingSampleActivity : AppCompatActivity() {
  companion object {
    /** The activity will set this when scrolling. */
    val scrollingIdlingResource =
      CountingIdlingResource("sentry-uitest-android-profilingSampleActivityScrolling")
  }

  private lateinit var binding: ActivityProfilingSampleBinding
  private val executor: ExecutorService = Executors.newFixedThreadPool(1)
  private var resumed = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      window.setSustainedPerformanceMode(true)
    }

    binding = ActivityProfilingSampleBinding.inflate(layoutInflater)
    setContentView(binding.root)

    // We show a simple list that changes the idling resource
    binding.profilingSampleList.apply {
      layoutManager = LinearLayoutManager(this@ProfilingSampleActivity)
      adapter = ProfilingSampleListAdapter()
      addOnScrollListener(
        object : RecyclerView.OnScrollListener() {
          override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            super.onScrollStateChanged(recyclerView, newState)
            if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
              scrollingIdlingResource.increment()
            }
            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
              scrollingIdlingResource.decrement()
            }
          }
        }
      )
    }
  }

  @Suppress("MagicNumber")
  override fun onResume() {
    super.onResume()
    resumed = true

    // Do operations until the activity is paused.
    executor.execute { fibonacci(50) }
  }

  private fun fibonacci(n: Int): Int =
    when {
      !resumed -> n // If we destroy the activity we stop this function
      n <= 1 -> 1
      else -> fibonacci(n - 1) + fibonacci(n - 2)
    }

  override fun onPause() {
    super.onPause()
    resumed = false
  }
}

/** Simple [RecyclerView.Adapter] that generates a bitmap to show for each item. */
internal class ProfilingSampleListAdapter : RecyclerView.Adapter<ViewHolder>() {
  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
    val binding =
      ProfilingSampleItemListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    return ViewHolder(binding)
  }

  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    holder.imageView.setImageBitmap(generateBitmap())
  }

  @Suppress("MagicNumber")
  private fun generateBitmap(): Bitmap {
    val bitmapSize = 256
    val colors =
      (0 until (bitmapSize * bitmapSize))
        .map { Color.rgb(Random.nextInt(256), Random.nextInt(256), Random.nextInt(256)) }
        .toIntArray()
    return Bitmap.createBitmap(colors, bitmapSize, bitmapSize, Bitmap.Config.ARGB_8888)
  }

  // Disables view recycling.
  override fun getItemViewType(position: Int): Int = position

  override fun getItemCount(): Int = 200
}

internal class ViewHolder(binding: ProfilingSampleItemListBinding) :
  RecyclerView.ViewHolder(binding.root) {
  val imageView: ImageView = binding.profilingSampleItemListImage
}
