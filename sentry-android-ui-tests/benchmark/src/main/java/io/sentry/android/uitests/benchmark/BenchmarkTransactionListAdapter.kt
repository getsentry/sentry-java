package io.sentry.android.uitests.benchmark

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlin.random.Random

/** Simple [RecyclerView.Adapter] that generates a bitmap and a text to show for each item. */
internal class BenchmarkTransactionListAdapter : RecyclerView.Adapter<ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.benchmark_item_list, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.imageView.setImageBitmap(generateBitmap())

        @SuppressLint("SetTextI18n")
        holder.textView.text = "Item $position ${"sentry ".repeat(position)}"
    }

    private fun generateBitmap(): Bitmap {
        val bitmapSize = 100
        val colors = (0 until (bitmapSize * bitmapSize)).map {
            Color.rgb(Random.nextInt(256), Random.nextInt(256), Random.nextInt(256))
        }.toIntArray()
        return Bitmap.createBitmap(colors, bitmapSize, bitmapSize, Bitmap.Config.ARGB_8888)
    }

    // Disables view recycling.
    override fun getItemViewType(position: Int): Int = position

    override fun getItemCount(): Int = 200
}

internal class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val imageView: ImageView = view.findViewById(R.id.benchmark_item_list_image)
    val textView: TextView = view.findViewById(R.id.benchmark_item_list_text)
}
