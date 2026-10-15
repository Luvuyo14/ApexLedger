package com.ntuli.apexledger.util

import android.annotation.SuppressLint
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.MotionEvent
import android.widget.EditText
import com.ntuli.apexledger.R

/** Adds a show/hide eye at the end of a password field. */
object PasswordToggle {
    @SuppressLint("ClickableViewAccessibility")
    fun attach(et: EditText) {
        var visible = false
        fun render() {
            et.transformationMethod =
                if (visible) HideReturnsTransformationMethod.getInstance() else PasswordTransformationMethod.getInstance()
            et.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, if (visible) R.drawable.ic_eye_off else R.drawable.ic_eye, 0)
            et.setSelection(et.text.length)
        }
        render()
        et.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val d = et.compoundDrawablesRelative[2]
                if (d != null && event.x >= et.width - et.paddingEnd - d.intrinsicWidth - 24) {
                    visible = !visible
                    render()
                    v.performClick()
                    return@setOnTouchListener true
                }
            }
            false
        }
    }
}
