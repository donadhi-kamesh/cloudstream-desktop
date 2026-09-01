package com.lagradost.cloudstream3.ui.settings

/**
 * CloudStream plugins call instance methods on this Kotlin object
 * (`Globals.INSTANCE.isLayout(int)` / `getLayout()`), not @JvmStatic helpers.
 */
object Globals {
    const val NONE = 0
    const val TV = 1
    const val EMULATOR = 2
    const val PHONE = 4

    fun getLayout(): Int = PHONE

    fun isLayout(flags: Int): Boolean = (getLayout() and flags) != 0

    fun isTvSettings(): Boolean = isLayout(TV)

    fun isEmulatorSettings(): Boolean = isLayout(EMULATOR)
}
