package com.ntuli.apexledger.util

import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.Toast
import com.google.android.material.snackbar.Snackbar

/** One consistent message style: green = done, red = problem, amber = needs your input. */
object Feedback {
    fun show(context: Context, message: String) {
        val m = message.lowercase()
        val kind = when {
            listOf("fail", "error", "denied", "could not", "unable", "incorrect", "wrong", "invalid", "not found").any { it in m } -> 1
            listOf("please", "required", "must", "enter", "select", "choose", "match").any { it in m } -> 2
            else -> 0
        }
        val root: View? = (context as? Activity)?.findViewById(android.R.id.content)
        if (root == null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            return
        }
        val (bg, fg) = when (kind) {
            1 -> 0xFFDC2626.toInt() to 0xFFFFFFFF.toInt()
            2 -> 0xFFF59E0B.toInt() to 0xFF1F2937.toInt()
            else -> 0xFF16A34A.toInt() to 0xFFFFFFFF.toInt()
        }
        Snackbar.make(root, message, if (kind == 1) Snackbar.LENGTH_LONG else Snackbar.LENGTH_SHORT)
            .setBackgroundTint(bg)
            .setTextColor(fg)
            .show()
    }
}
