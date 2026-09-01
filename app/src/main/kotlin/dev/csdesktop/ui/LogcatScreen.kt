package dev.csdesktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.csdesktop.log.Logcat
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@Composable
fun LogcatScreen() {
    val lines by Logcat.lines.collectAsState()
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex)
    }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("Logcat", style = MaterialTheme.typography.headlineMedium)
        Text(
            Logcat.file.absolutePath,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(Logcat.snapshot()), null)
            }) { Text("Copy") }
            TextButton(onClick = { Logcat.clear() }) { Text("Clear") }
            TextButton(onClick = {
                runCatching { Desktop.getDesktop().open(Logcat.file.parentFile) }
            }) { Text("Open folder") }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().background(Color(0xFF111111)).padding(8.dp),
        ) {
            itemsIndexed(lines) { _, line ->
                val color = when (line.level) {
                    "E" -> Color(0xFFFF8A80)
                    "W" -> Color(0xFFFFD54F)
                    "I" -> Color(0xFFB2FF59)
                    else -> Color(0xFFB0BEC5)
                }
                Text(
                    line.text,
                    color = color,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                )
            }
        }
    }
}
