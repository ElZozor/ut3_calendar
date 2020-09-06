package com.edt.ut3.ui.custom_views.image_preview

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.edt.ut3.R
import com.edt.ut3.backend.note.Picture
import com.edt.ut3.backend.preferences.PreferencesManager
import com.edt.ut3.misc.toDp
import com.edt.ut3.ui.preferences.Theme
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ImagePreviewAdapter(var dataset: List<Picture>) : RecyclerView.Adapter<ImagePreviewAdapter.ImageViewHolder>() {

    companion object {
        fun from(layout: RecyclerView): ImagePreviewAdapter {
            return layout.adapter as ImagePreviewAdapter
        }
    }

    var onItemClickListener: ((v: View, picture: Picture, pictures: List<Picture>) -> Unit)? = null
    var onAddPictureClickListener: ((v: View) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ImageViewHolder(
        CardView(parent.context, null).apply {
            addView(ImageView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            })

            radius = 8.toDp(parent.context)
        }
    )

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val size = 64.toDp(holder.imgView.context).toInt()

        holder.imgView.cardElevation = 0f
        holder.setIsRecyclable(false)

        if (position == dataset.size) {
            bindAddPicture(holder)
            holder.imgView.setOnClickListener { onAddPictureClickListener?.invoke(it) }
        } else {
            val imgView = holder.imgView.getChildAt(0) as ImageView
            GlobalScope.launch {
                println(dataset[position])
                val thumbnail = withContext(IO) { dataset[position].loadThumbnail() }
                withContext(Main) {
                    imgView.setImageBitmap(thumbnail)
                    imgView.scaleType = ImageView.ScaleType.CENTER_CROP
                    imgView.setOnClickListener { onItemClickListener?.invoke(imgView, dataset[position], dataset) }
                }
            }
        }

        holder.imgView.layoutParams = ViewGroup.LayoutParams(size, size)
    }

    private fun bindAddPicture(holder: ImageViewHolder) {
        (holder.imgView.getChildAt(0) as ImageView).apply {
            setImageResource(R.drawable.ic_add)

            when (PreferencesManager(context).getTheme()) {
                Theme.DARK ->  {
                    setColorFilter(Color.WHITE)
                    holder.imgView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.colorPrimaryDark))
                }

                Theme.LIGHT -> {
                    setColorFilter(Color.BLACK)
                    holder.imgView.setCardBackgroundColor(Color.parseColor("#DCDCDC"))
                }
            }

            val imgSize = 24.toDp(context).toInt()
            layoutParams = FrameLayout.LayoutParams(imgSize, imgSize).apply {
                gravity = Gravity.CENTER
            }
        }
    }

    override fun getItemCount() = dataset.size + 1

    class ImageViewHolder(val imgView: CardView) : RecyclerView.ViewHolder(imgView)

}