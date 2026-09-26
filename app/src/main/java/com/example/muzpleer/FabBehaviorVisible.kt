package com.example.muzpleer

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton

class FabBehaviorVisible(context: Context?, attrs: AttributeSet?) :
    FloatingActionButton.Behavior() {
    @SuppressLint("RestrictedApi")
    override fun onNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: FloatingActionButton,
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        type: Int
    ) {
        super.onNestedScroll(
            coordinatorLayout,
            child,
            target,
            dxConsumed,
            dyConsumed,
            dxUnconsumed,
            dyUnconsumed,
            type
        )
        if (dyConsumed > 0 && child.visibility == View.VISIBLE) {
            Log.d(TAG, "FabBehavior onNestedScroll dyConsumed > 0")
            child.visibility = View.INVISIBLE
        } else if (dyConsumed < 0 && child.visibility != View.VISIBLE) {
            Log.d(TAG, "FabBehavior onNestedScroll dyConsumed < 0")
            child.visibility = View.VISIBLE
        }
    }

    override fun onStartNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: FloatingActionButton,
        directTargetChild: View,
        target: View,
        axes: Int,
        type: Int
    ): Boolean {
        return (axes == ViewCompat.SCROLL_AXIS_VERTICAL
                || super.onStartNestedScroll(
            coordinatorLayout,
            child,
            directTargetChild,
            target,
            axes,
            type
        ))
    }

    companion object {
        private const val TAG = "33333"
    }
}