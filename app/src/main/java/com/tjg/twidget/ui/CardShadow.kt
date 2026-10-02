package com.tjg.twidget.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import com.tjg.twidget.R

internal object CardShadow {
    const val PADDING_DP = 36
    fun paint(context: Context, blurDp: Float = 30f, offsetDp: Float = 6f, shadowColor: Int = 0x29000000) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.oneui_card_bg)
        val density = context.resources.displayMetrics.density
        setShadowLayer(blurDp * density, 0f, offsetDp * density, shadowColor)
    }
}

/** Includes a clickable card's surface and ripple mask in the same shape. */
internal fun Drawable.setCardCornerRadius(radius: Float) {
    when (this) {
        is GradientDrawable -> cornerRadius = radius
        is LayerDrawable -> for (index in 0 until numberOfLayers) {
            getDrawable(index).setCardCornerRadius(radius)
        }
    }
}

internal class CardShadowView(context: Context, private val radius: Float) : View(context) {
    private val paint = CardShadow.paint(context, blurDp = 8f, offsetDp = 2f, shadowColor = 0x18000000)
    private val inset = CardShadow.PADDING_DP * resources.displayMetrics.density
    init { setLayerType(LAYER_TYPE_SOFTWARE, null) }
    override fun onDraw(canvas: Canvas) {
        canvas.drawRoundRect(inset, inset, width - inset, height - inset, radius, radius, paint)
    }
}
