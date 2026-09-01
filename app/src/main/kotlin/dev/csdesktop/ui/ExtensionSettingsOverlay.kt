package dev.csdesktop.ui

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.TextView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button as ComposeButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import dev.csdesktop.extloader.ExtensionUi

/**
 * Renders plugin Android view trees as Material 3 widgets inside the Compose window.
 * That is the desktop equivalent of CloudStream's BottomSheetDialogFragment: same
 * widgets (text, switches, fields, buttons), same theme as the rest of the app, and
 * clicks actually land because they are not in a competing Swing dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionSettingsOverlay() {
    DisposableEffect(Unit) {
        ExtensionUi.composeAttached = true
        onDispose { ExtensionUi.composeAttached = false }
    }
    val sessions by ExtensionUi.sessions.collectAsState()
    val session = sessions.lastOrNull() ?: return
    val tick by session.tick.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { session.handle.dispose() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            val heading = session.title?.takeIf { it.isNotBlank() } ?: "Extension"
            Text(heading, style = MaterialTheme.typography.titleLarge)
            val body = session.message?.takeIf { it.isNotBlank() }
            if (body != null) {
                Spacer(Modifier.height(8.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                @Suppress("UNUSED_EXPRESSION")
                tick
                val root = session.root
                if (root != null) {
                    AndroidViewTree(root)
                }
            }
            if (session.buttons.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                            session.buttons.forEach { btn ->
                                val click = {
                                    if (btn.onClick != null) btn.onClick.run()
                                    else session.handle.dispose()
                                }
                                if (btn.destructive) {
                                    OutlinedButton(onClick = click) { Text(btn.label) }
                                } else {
                                    ComposeButton(onClick = click) { Text(btn.label) }
                                }
                            }
                }
            }
        }
    }
}

@Composable
private fun AndroidViewTree(view: View) {
    if (view.visibility == View.GONE) return
    when (view) {
        is EditText -> {
            OutlinedTextField(
                value = view.text?.toString().orEmpty(),
                onValueChange = { view.setText(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    val hint = view.hint?.toString().orEmpty()
                    if (hint.isNotBlank()) Text(hint)
                },
                singleLine = true,
            )
        }
        is RadioButton -> LabeledToggle(
            label = view.text?.toString().orEmpty(),
            checked = view.isChecked,
            switch = false,
            onChecked = { view.isChecked = it },
        )
        is android.widget.Switch, is androidx.appcompat.widget.SwitchCompat -> {
            val box = view as CompoundButton
            LabeledToggle(
                label = box.text?.toString().orEmpty(),
                checked = box.isChecked,
                switch = true,
                onChecked = { box.isChecked = it },
            )
        }
        is CompoundButton -> LabeledToggle(
            label = view.text?.toString().orEmpty(),
            checked = view.isChecked,
            switch = false,
            onChecked = { view.isChecked = it },
        )
        is ImageButton, is Button -> {
            val label = when {
                view is TextView && !view.text.isNullOrBlank() -> view.text.toString()
                else -> "Save"
            }
            ComposeButton(onClick = { click(view) }, modifier = Modifier.fillMaxWidth()) {
                Text(label)
            }
        }
        is TextView -> {
            val text = view.text?.toString().orEmpty()
            if (text.isNotBlank()) {
                Text(text, style = MaterialTheme.typography.bodyLarge)
            }
        }
        is ScrollView, is NestedScrollView, is RecyclerView -> {
            val group = view as ViewGroup
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 0 until group.childCount) {
                    val child = group.getChildAt(i) ?: continue
                    AndroidViewTree(child)
                }
            }
        }
        is ViewGroup -> {
            val horizontal = view is LinearLayout && view.orientation == LinearLayout.HORIZONTAL
            if (horizontal) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (i in 0 until view.childCount) {
                        val child = view.getChildAt(i) ?: continue
                        AndroidViewTree(child)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (i in 0 until view.childCount) {
                        val child = view.getChildAt(i) ?: continue
                        AndroidViewTree(child)
                    }
                }
            }
        }
    }
}

private fun click(view: View) {
    runCatching {
        view.javaClass.methods.first { it.name == "performClick" && it.parameterCount == 0 }.invoke(view)
    }
}

@Composable
private fun LabeledToggle(
    label: String,
    checked: Boolean,
    switch: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label.ifBlank { " " }, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (switch) {
            Switch(checked = checked, onCheckedChange = onChecked)
        } else {
            Checkbox(checked = checked, onCheckedChange = onChecked)
        }
    }
}
