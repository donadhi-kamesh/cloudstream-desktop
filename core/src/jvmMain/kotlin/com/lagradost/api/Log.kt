package com.lagradost.api

actual object Log {
    /** Optional desktop logcat sink. Levels: V D I W E */
    @JvmField
    var sink: ((String, String, String) -> Unit)? = null

    actual fun d(tag: String, message: String) = emit("D", tag, message)
    actual fun i(tag: String, message: String) = emit("I", tag, message)
    actual fun w(tag: String, message: String) = emit("W", tag, message)
    actual fun e(tag: String, message: String) = emit("E", tag, message)

    private fun emit(level: String, tag: String, message: String) {
        println("$level/$tag: $message")
        try {
            sink?.invoke(level, tag, message)
        } catch (_: Throwable) {
        }
    }
}
