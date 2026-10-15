package com.ntuli.apexledger.util

import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.ntuli.apexledger.R

object StatCards {
    /** Coloured accent bar + a small vector icon on a tile tinted with the same colour. */
    fun decorate(card: View, iconRes: Int, colorRes: Int) {
        val color = ContextCompat.getColor(card.context, colorRes)
        card.findViewById<View>(R.id.statAccentBar).setBackgroundColor(color)
        val icon = card.findViewById<ImageView>(R.id.tvStatIcon)
        icon.setImageResource(iconRes)
        icon.setColorFilter(color)
        icon.visibility = View.VISIBLE
        icon.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 12f * card.resources.displayMetrics.density
            setColor((color and 0x00FFFFFF) or 0x22000000)
        }
    }

    fun initials(name: String?): String =
        (name ?: "").split(" ").filter { it.isNotEmpty() }.take(2)
            .joinToString("") { it.first().uppercase() }.ifEmpty { "--" }
}
