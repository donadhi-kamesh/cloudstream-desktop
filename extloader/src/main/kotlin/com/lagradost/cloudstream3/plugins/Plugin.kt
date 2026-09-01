package com.lagradost.cloudstream3.plugins

import android.content.Context
import android.content.res.Resources
import com.lagradost.api.Log
import com.lagradost.cloudstream3.actions.VideoClickAction
import com.lagradost.cloudstream3.actions.VideoClickActionHolder

/**
 * Android plugin entry. Official `.cs3` extensions extend this class and override
 * [load] with a Context. Desktop supplies a stub Context.
 */
abstract class Plugin : BasePlugin() {
    @Throws(Throwable::class)
    open fun load(context: Context) {
        load()
    }

    fun registerVideoClickAction(element: VideoClickAction) {
        Log.i(PLUGIN_TAG, "Adding ${element.name} VideoClickAction")
        element.sourcePlugin = this.filename
        VideoClickActionHolder.allVideoClickActions.add(element)
    }

    var resources: Resources? = null
    var openSettings: ((context: Context) -> Unit)? = null
}
