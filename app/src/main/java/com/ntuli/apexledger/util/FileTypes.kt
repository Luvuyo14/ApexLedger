package com.ntuli.apexledger.util

import android.graphics.drawable.GradientDrawable
import android.widget.TextView

object FileTypes {
    /** Small coloured tile with the file extension (PDF red, images blue, others grey). */
    fun apply(tile: TextView, fileName: String) {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val (label, color) = when (ext) {
            "pdf" -> "PDF" to 0xFFDC2626.toInt()
            "jpg", "jpeg" -> "JPG" to 0xFF2563EB.toInt()
            "png" -> "PNG" to 0xFF0EA5E9.toInt()
            "" -> "FILE" to 0xFF64748B.toInt()
            else -> ext.uppercase().take(4) to 0xFF64748B.toInt()
        }
        tile.text = label
        tile.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 10f * tile.resources.displayMetrics.density
            setColor(color)
        }
    }
}
