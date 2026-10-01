package com.tjg.twidget.ui

import android.content.Context
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.roundToInt

/** Fits a media item by its decoded aspect ratio rather than cropping a fixed rectangle. */
class MediaAspectImageView(context: Context) : AppCompatImageView(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val image = drawable
        if (image == null || image.intrinsicWidth <= 0 || image.intrinsicHeight <= 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        val ratio = image.intrinsicWidth.toFloat() / image.intrinsicHeight
        when {
            MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY &&
                MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.EXACTLY -> {
                val height = MeasureSpec.getSize(heightMeasureSpec)
                setMeasuredDimension(resolveSize((height * ratio).roundToInt(), widthMeasureSpec), height)
            }
            MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY &&
                MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.EXACTLY -> {
                val width = MeasureSpec.getSize(widthMeasureSpec)
                setMeasuredDimension(width, resolveSize((width / ratio).roundToInt(), heightMeasureSpec))
            }
            else -> super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }
}
