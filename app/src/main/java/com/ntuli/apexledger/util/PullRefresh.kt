package com.ntuli.apexledger.util

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.ntuli.apexledger.R

/**
 * Wraps a screen's content in a pull-to-refresh layout. Call right after setContentView().
 * The spinner also shows briefly on first load so people see the app is working.
 */
object PullRefresh {
    fun attach(activity: Activity, onRefresh: () -> Unit): SwipeRefreshLayout? {
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val child = content.getChildAt(0) ?: return null
        content.removeView(child)

        val srl = SwipeRefreshLayout(activity)
        srl.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        srl.addView(child, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        content.addView(srl)

        srl.setColorSchemeResources(R.color.primary)
        // Only start a refresh when the visible list/scroll area is at the very top
        srl.setOnChildScrollUpCallback { _, _ -> firstScrollable(child)?.canScrollVertically(-1) ?: false }
        srl.setOnRefreshListener {
            onRefresh()
            srl.postDelayed({ srl.isRefreshing = false }, 900)
        }

        // First-load indicator
        srl.isRefreshing = true
        srl.postDelayed({ srl.isRefreshing = false }, 1100)
        return srl
    }

    private fun firstScrollable(v: View): View? {
        if (v.visibility != View.VISIBLE) return null
        if (v is RecyclerView || v is android.widget.ScrollView || v is NestedScrollView) return v
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                firstScrollable(v.getChildAt(i))?.let { return it }
            }
        }
        return null
    }
}
