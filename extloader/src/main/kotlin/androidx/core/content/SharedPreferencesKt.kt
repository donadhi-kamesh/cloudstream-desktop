package androidx.core.content

import android.content.SharedPreferences

fun SharedPreferences.edit(
    commit: Boolean = false,
    action: SharedPreferences.Editor.() -> Unit,
) {
    val editor = edit()
    action(editor)
    if (commit) editor.commit() else editor.apply()
}
