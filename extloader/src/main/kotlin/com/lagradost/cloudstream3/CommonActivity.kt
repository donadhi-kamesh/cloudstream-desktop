package com.lagradost.cloudstream3

import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.util.DisplayMetrics
import android.view.KeyEvent
import android.widget.Toast
import com.lagradost.api.Log
import com.lagradost.cloudstream3.utils.Event

/**
 * Plugin-facing CloudStream CommonActivity. Kotlin plugins call
 * [getActivity] / [activity]; keep both the static getter and the property.
 */
object CommonActivity {
    const val TAG = "COMPACT"

    /**
     * Plugins compiled against CloudStream call `CommonActivity.getActivity()` as an
     * instance method on the Kotlin object (`invokevirtual`). Keep this a property
     * without @JvmStatic so the JVM method is non-static.
     */
    var activity: Activity? = null

    @JvmStatic
    var isPipDesired: Boolean = false

    @JvmStatic
    var isInPIPMode: Boolean = false

    @JvmStatic
    val onDialogDismissedEvent = Event<Int>()

    @JvmStatic
    val onColorSelectedEvent = Event<Pair<Int, Int>>()

    @JvmStatic
    fun setActivityInstance(newActivity: Activity?) {
        activity = newActivity
    }

    @JvmStatic
    val displayMetrics: DisplayMetrics
        get() = Resources.getSystem().displayMetrics

    @JvmStatic
    val screenWidth: Int
        get() = maxOf(displayMetrics.widthPixels, displayMetrics.heightPixels)

    @JvmStatic
    val screenHeight: Int
        get() = minOf(displayMetrics.widthPixels, displayMetrics.heightPixels)

    @JvmStatic
    val screenWidthWithOrientation: Int
        get() = displayMetrics.widthPixels

    @JvmStatic
    val screenHeightWithOrientation: Int
        get() = displayMetrics.heightPixels

    @JvmStatic
    @JvmOverloads
    fun showToast(message: Int, duration: Int? = null) {
        showToast(activity?.getString(message).orEmpty(), duration)
    }

    @JvmStatic
    @JvmOverloads
    fun showToast(message: String?, duration: Int? = null) {
        showToast(activity, message, duration)
    }

    @JvmStatic
    @JvmOverloads
    fun showToast(act: Activity?, message: Int, duration: Int? = null) {
        showToast(act, act?.getString(message), duration)
    }

    @JvmStatic
    @JvmOverloads
    fun showToast(act: Activity?, message: String?, duration: Int? = null) {
        if (message.isNullOrBlank()) return
        Log.i(TAG, "showToast = $message")
        val ctx: Context = act ?: activity ?: return
        val dur = duration ?: Toast.LENGTH_SHORT
        javax.swing.SwingUtilities.invokeLater {
            Toast.makeText(ctx, message, dur).show()
        }
    }

    @JvmStatic
    fun init(act: Activity?) {
        setActivityInstance(act)
    }

    @JvmStatic
    fun onUserLeaveHint(act: Activity?) {}

    @JvmStatic
    fun updateTheme(act: Activity?) {}

    @JvmStatic
    fun loadThemes(act: Activity?) {}

    @JvmStatic
    fun onKeyDown(act: Activity?, keyCode: Int, event: KeyEvent?): Boolean? = null

    @JvmStatic
    fun dispatchKeyEvent(act: Activity?, event: KeyEvent?): Boolean? = null
}

enum class FocusDirection {
    Start,
    End,
    Up,
    Down,
}
