package com.lagradost.cloudstream3.actions

/**
 * Android CloudStream lets plugins register extra "click" actions on a result.
 * Desktop keeps the type so plugins resolve; the UI currently ignores these.
 */
abstract class VideoClickAction {
    abstract val name: String
    var sourcePlugin: String? = null
}

object VideoClickActionHolder {
    val allVideoClickActions: MutableList<VideoClickAction> = mutableListOf()
}
