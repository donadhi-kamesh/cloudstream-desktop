package dev.csdesktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import dev.csdesktop.AppState
import dev.csdesktop.Destination
import dev.csdesktop.data.ResumeEntry
import dev.csdesktop.ui.theme.CsPurple

@Composable
fun PosterCard(
    title: String,
    poster: String?,
    badge: String? = null,
    progress: Float? = null,
    onClick: () -> Unit,
) {
    Column(
        Modifier.width(120.dp).clickable(onClick = onClick).padding(4.dp)
    ) {
        Box(
            Modifier.size(width = 120.dp, height = 170.dp).clip(RoundedCornerShape(8.dp))
        ) {
            if (poster.isNullOrBlank()) {
                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(title.take(1), style = MaterialTheme.typography.headlineMedium)
                }
            } else {
                AsyncImage(
                    model = poster,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (badge != null) {
                Text(
                    badge,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xCC000000)).padding(horizontal = 4.dp, vertical = 2.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            if (progress != null && progress > 0f) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color(0x66000000)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress.coerceIn(0.02f, 1f))
                            .fillMaxHeight()
                            .background(CsPurple),
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
    }
}

fun watchProgress(positionMs: Long, durationMs: Long): Float? {
    if (positionMs < 1_000) return null
    if (durationMs <= 1_000) return 0.08f
    return (positionMs.toFloat() / durationMs).coerceIn(0.02f, 1f)
}

fun watchBadge(positionMs: Long, durationMs: Long): String? {
    if (positionMs < 1_000) return null
    val pos = positionMs / 1000
    val h = pos / 3600
    val m = (pos % 3600) / 60
    val s = pos % 60
    val clock = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    if (durationMs <= 1_000) return clock
    val tot = durationMs / 1000
    val th = tot / 3600
    val tm = (tot % 3600) / 60
    val total = if (th > 0) "%d:%02d".format(th, tm) else "%d:%02d".format(tm, tot % 60)
    return "$clock / $total"
}

@Composable
fun EmptyState(title: String, body: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
fun HomeScreen(state: AppState) {
    val rows by state.home.collectAsState()
    val loading by state.homeLoading.collectAsState()
    val error by state.homeError.collectAsState()
    val plugins by state.plugins.collectAsState()
    val filter = state.settingsStore.settings.homePluginFilter
    val continueWatching by state.continueWatching.collectAsState()

    if (plugins.none { it.enabled } && rows.isEmpty() && !loading) {
        EmptyState(
            title = "No extensions installed",
            body = "CloudStream Desktop ships with zero video sources. Add a repository and install a legal plugin such as iptv-org or YouTube from the official extensions repo.",
            action = "Open Extensions",
            onAction = { state.go(Destination.Extensions) },
        )
        return
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
        item {
            Text("Home", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = filter.isBlank(),
                        onClick = { state.setHomePluginFilter("") },
                        label = { Text("All Extensions") },
                    )
                }
                items(plugins.filter { it.enabled }) { p ->
                    FilterChip(
                        selected = filter == p.displayName,
                        onClick = { state.setHomePluginFilter(if (filter == p.displayName) "" else p.displayName) },
                        label = { Text(p.displayName) },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            androidx.compose.material3.OutlinedTextField(
                value = "",
                onValueChange = { query ->
                    if (query.isNotBlank()) {
                        val prov = filter.takeIf { it.isNotBlank() }
                        state.search(query, prov)
                        state.go(Destination.Search)
                    }
                },
                modifier = Modifier.fillMaxWidth().clickable { state.go(Destination.Search) },
                placeholder = { Text(if (filter.isNotBlank()) "Search in $filter…" else "Search across all extensions…") },
                singleLine = true,
                readOnly = false,
            )
            Spacer(Modifier.height(16.dp))
        }
        if (continueWatching.isNotEmpty()) {
            item {
                Text("Continue watching", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                LazyRow {
                    items(continueWatching) { r: ResumeEntry ->
                        PosterCard(
                            r.title,
                            r.poster,
                            badge = watchBadge(r.positionMs, r.durationMs),
                            progress = watchProgress(r.positionMs, r.durationMs),
                            onClick = { state.resumePlayback(r) },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        if (loading) {
            item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
        if (error != null && rows.isEmpty()) {
            item { Text(error!!, color = MaterialTheme.colorScheme.error) }
        }
        items(rows) { row ->
            Text("${row.list.name} · ${row.provider}", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            LazyRow {
                items(row.list.list) { item: SearchResponse ->
                    PosterCard(
                        title = item.name,
                        poster = item.posterUrl,
                        badge = if (item.type == TvType.Live) "LIVE" else null,
                        onClick = { state.openResult(item.apiName, item.url, item.name) },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
