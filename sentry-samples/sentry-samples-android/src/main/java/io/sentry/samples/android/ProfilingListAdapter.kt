package io.sentry.samples.android

import android.graphics.Bitmap
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import io.sentry.samples.android.databinding.ProfilingItemListBinding
import kotlin.random.Random

class ProfilingListAdapter : RecyclerView.Adapter<ViewHolder>() {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val binding = ProfilingItemListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        holder.imageView.setImageBitmap(generateBitmap())
    }

    @Suppress("MagicNumber")
    private fun generateBitmap(): Bitmap {
        val bitmapSize = 128
        val colors =
            (0 until (bitmapSize * bitmapSize))
                .map {
                    Color.rgb(Random.nextInt(256), Random.nextInt(256), Random.nextInt(256))
                }.toIntArray()
        return Bitmap.createBitmap(colors, bitmapSize, bitmapSize, Bitmap.Config.ARGB_8888)
    }

    // Disables view recycling.
    override fun getItemViewType(position: Int): Int = position

    override fun getItemCount(): Int = 200
}

class ViewHolder(
    binding: ProfilingItemListBinding,
) : RecyclerView.ViewHolder(binding.root) {
    val imageView: ImageView = binding.benchmarkItemListImage
}
