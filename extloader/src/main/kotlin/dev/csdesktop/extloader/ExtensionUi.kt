package dev.csdesktop.extloader

import android.view.View
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

/**
 * Bridge from plugin Android dialogs into the Compose app.
 *
 * Swing JDialogs sit in a different windowing toolkit than Compose Desktop, so
 * checkboxes in extension menus often never receive clicks. The app renders the
 * same Android view tree as a Material bottom sheet instead, which is what the
 * Android app does (a BottomSheetDialogFragment over the activity).
 */
object ExtensionUi {
    private val ids = AtomicLong(1)

    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    val sessions: StateFlow<List<Session>> = _sessions

    /**
     * Plugin [load] often pops a first-run notice. Those belong behind Settings,
     * matching Android where openSettings is only invoked from the settings button.
     */
    @Volatile
    var suppressPopups: Boolean = false

    /** True while the Compose overlay is on screen and can collect [sessions]. */
    @Volatile
    var composeAttached: Boolean = false

    fun present(
        title: String?,
        message: String?,
        content: View?,
        buttons: List<DialogHost.DialogButton>?,
        onDismiss: Runnable?,
    ): DialogHost.Handle {
        val handle = DialogHost.Handle()
        if (suppressPopups) {
            com.lagradost.api.Log.i(
                "ExtensionUi",
                "suppressed plugin popup during load: ${title ?: message ?: content?.javaClass?.simpleName}",
            )
            return handle
        }
        val session = Session(
            id = ids.getAndIncrement(),
            title = title,
            message = message,
            root = content,
            buttons = buttons.orEmpty(),
            onDismiss = onDismiss,
            handle = handle,
        )
        handle.attachSession(session.id)
        if (content != null) {
            content.setDesktopTreeListener {
                session.tick.value = session.tick.value + 1
            }
        }
        _sessions.update { it + session }
        return handle
    }

    fun dismiss(id: Long) {
        var dismissed: Session? = null
        _sessions.update { list ->
            val found = list.firstOrNull { it.id == id }
            dismissed = found
            if (found == null) list else list.filter { it.id != id }
        }
        val session = dismissed ?: return
        session.root?.setDesktopTreeListener(null)
        runCatching { session.onDismiss?.run() }
    }

    fun dismissAll() {
        _sessions.value.map { it.id }.forEach { dismiss(it) }
    }

    class Session internal constructor(
        val id: Long,
        val title: String?,
        val message: String?,
        val root: View?,
        val buttons: List<DialogHost.DialogButton>,
        val onDismiss: Runnable?,
        val handle: DialogHost.Handle,
        val tick: MutableStateFlow<Int> = MutableStateFlow(0),
    )
}
