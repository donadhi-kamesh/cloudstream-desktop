package dev.csdesktop.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.csdesktop.AppState
import dev.csdesktop.download.DownloadStatus

@Composable
fun LibraryScreen(state: AppState) {
    var tab by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Library", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        TabRow(tab) {
            Tab(tab == 0, onClick = { tab = 0 }, text = { Text("Bookmarks") })
            Tab(tab == 1, onClick = { tab = 1 }, text = { Text("History") })
        }
        Spacer(Modifier.height(12.dp))
        when (tab) {
            0 -> {
                val items = state.bookmarks
                if (items.isEmpty()) {
                    EmptyState("No bookmarks", "Bookmark a title from its result page.")
                } else {
                    LazyVerticalGrid(columns = GridCells.Adaptive(120.dp)) {
                        items(items) { b ->
                            PosterCard(b.title, b.poster) {
                                state.openResult(b.provider.orEmpty(), b.url.orEmpty(), b.title)
                            }
                        }
                    }
                }
            }
            else -> {
                val items = state.history
                if (items.isEmpty()) {
                    EmptyState("No history", "Watched titles appear here.")
                } else {
                    LazyVerticalGrid(columns = GridCells.Adaptive(120.dp)) {
                        items(items) { h ->
                            PosterCard(
                                h.title,
                                h.poster,
                                badge = watchBadge(h.positionMs, h.durationMs),
                                progress = watchProgress(h.positionMs, h.durationMs),
                            ) {
                                val resume = state.library.resume(h.key)
                                if (resume != null) state.resumePlayback(resume)
                                else state.openResult(h.provider.orEmpty(), h.url.orEmpty(), h.title)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadsScreen(state: AppState) {
    val items = state.downloads.list()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Downloads", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text("Saved to ${state.settingsStore.settings.downloadFolder}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        if (items.isEmpty()) {
            EmptyState("Download queue is empty", "Use Download on a result page for progressive files. HLS uses ffmpeg when present.")
        } else {
            items.forEach { item ->
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                Text("${item.status} · ${item.bytesDone} / ${item.bytesTotal} bytes", style = MaterialTheme.typography.bodyMedium)
                if (item.bytesTotal > 0) {
                    LinearProgressIndicator(progress = { (item.bytesDone.toFloat() / item.bytesTotal).coerceIn(0f, 1f) })
                }
                if (item.status == DownloadStatus.Running) {
                    TextButton(onClick = { state.downloads.pause(item.id) }) { Text("Pause") }
                } else if (item.status == DownloadStatus.Paused || item.status == DownloadStatus.Queued) {
                    TextButton(onClick = { state.downloads.resume(item.id) }) { Text("Resume") }
                }
                if (item.error != null) Text(item.error!!, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
