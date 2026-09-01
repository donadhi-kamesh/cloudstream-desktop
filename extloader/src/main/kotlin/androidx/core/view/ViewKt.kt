package androidx.core.view

import android.view.View

var View.isVisible: Boolean
    get() = visibility == View.VISIBLE
    set(value) {
        visibility = if (value) View.VISIBLE else View.GONE
    }

var View.isInvisible: Boolean
    get() = visibility == View.INVISIBLE
    set(value) {
        visibility = if (value) View.INVISIBLE else View.VISIBLE
    }

var View.isGone: Boolean
    get() = visibility == View.GONE
    set(value) {
        visibility = if (value) View.GONE else View.VISIBLE
    }
