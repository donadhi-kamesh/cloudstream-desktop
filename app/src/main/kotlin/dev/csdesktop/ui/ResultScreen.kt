package dev.csdesktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.TvType
import dev.csdesktop.AppState
import dev.csdesktop.Destination

@Composable
fun ResultScreen(state: AppState, dest: Destination.Result) {
    val loaded by state.load.collectAsState()
    val error by state.loadError.collectAsState()
    val loading by state.loadLoading.collectAsState()
    Column(Modifier.fillMaxSize()) {
        TextButtonBack { state.go(Destination.Home) }
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null && loaded == null -> Text(
                error!!,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp),
            )
            loaded == null -> Text("Nothing loaded.", modifier = Modifier.padding(16.dp))
            else -> DetailsBody(state, loaded!!)
        }
    }
}

@Composable
private fun DetailsBody(state: AppState, item: LoadResponse) {
    val backdrop = item.backgroundPosterUrl ?: item.posterUrl
    val anime = item as? AnimeLoadResponse
    val dubKeys = anime?.episodes?.keys?.sortedBy { it.ordinal } ?: emptyList()
    var dubFilter by remember(item.apiName, item.url) {
        mutableStateOf(dubKeys.firstOrNull { it == DubStatus.Subbed } ?: dubKeys.firstOrNull())
    }
    val episodeItems = when (item) {
        is TvSeriesLoadResponse -> item.episodes.map { EpisodeRow(it, null) }
        is AnimeLoadResponse -> {
            val status = dubFilter
            val list = if (status != null) item.episodes[status].orEmpty()
            else item.episodes.values.flatten()
            list.map { EpisodeRow(it, status?.name) }
        }
        else -> emptyList()
    }.sortedWith(
        compareBy(
            { it.episode.season ?: Int.MAX_VALUE },
            { it.episode.episode ?: Int.MAX_VALUE },
        )
    )
    val seasons = episodeItems.map { it.episode.season }.distinct()
    var seasonFilter by remember(item.apiName, item.url, dubFilter) {
        mutableStateOf(seasons.mapNotNull { it }.minOrNull())
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Box(Modifier.fillMaxWidth().height(280.dp)) {
                AsyncImage(
                    model = backdrop,
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background)),
                    ),
                )
                Row(
                    Modifier.align(Alignment.BottomStart).padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    AsyncImage(
                        model = item.posterUrl,
                        contentDescription = item.name,
                        modifier = Modifier.width(120.dp).height(180.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(item.name, style = MaterialTheme.typography.headlineMedium, maxLines = 2)
                        val status = (item as? TvSeriesLoadResponse)?.showStatus
                            ?: (item as? AnimeLoadResponse)?.showStatus
                        val meta = buildString {
                            append(item.type.name)
                            append(" · ")
                            append(item.apiName)
                            item.year?.let { append(" · $it") }
                            item.score?.toStringNull(0.1, 10)?.let { append(" · ★ $it") }
                            item.contentRating?.let { append(" · $it") }
                            status?.let { append(" · ${it.name}") }
                        }
                        Text(meta, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            item.plot.orEmpty().ifBlank { "No plot provided by this provider." },
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(12.dp))
                        val itemKey = "${item.apiName}|${item.url}"
                        val resume = remember(itemKey) { state.library.resume(itemKey) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (resume != null && resume.positionMs > 2_000) {
                                Button(onClick = { state.playFromLoad(item, startFromBeginning = false) }) {
                                    Text("Resume (${formatTime(resume.positionMs / 1000.0)})")
                                }
                                OutlinedButton(onClick = { state.playFromLoad(item, startFromBeginning = true) }) {
                                    Text("From Beginning")
                                }
                            } else {
                                Button(onClick = { state.playFromLoad(item, startFromBeginning = false) }) {
                                    Text("Play")
                                }
                            }
                            OutlinedButton(onClick = { state.toggleBookmark(item) }) { Text("Bookmark") }
                            if (item is MovieLoadResponse) {
                                OutlinedButton(onClick = {
                                    state.downloads.enqueue(item.name, item.dataUrl, null, emptyMap())
                                    state.go(Destination.Downloads)
                                }) { Text("Download") }
                            }
                        }
                    }
                }
            }
        }
        item {
            LazyRow(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(item.tags.orEmpty()) { tag -> AssistPlain(tag) }
            }
        }
        if (dubKeys.size > 1) {
            item {
                Text("Audio", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(dubKeys) { status ->
                        FilterChip(
                            selected = dubFilter == status,
                            onClick = { dubFilter = status },
                            label = {
                                Text(
                                    when (status) {
                                        DubStatus.Subbed -> "Sub"
                                        DubStatus.Dubbed -> "Dub"
                                        else -> status.name
                                    } + " (${anime?.episodes?.get(status)?.size ?: 0})",
                                )
                            },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        if (episodeItems.isNotEmpty()) {
            item {
                Text("Episodes", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(8.dp))
                if (seasons.size > 1 || seasons.any { it != null }) {
                    LazyRow(
                        Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(seasons) { season ->
                            FilterChip(
                                selected = seasonFilter == season,
                                onClick = { seasonFilter = season },
                                label = { Text(if (season == null) "Specials" else "Season $season") },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            val shown = episodeItems.filter { it.episode.season == seasonFilter || seasons.size <= 1 }
            items(shown) { row ->
                val ep = row.episode
                val epKey = "${item.apiName}|${item.url}|${ep.data}"
                val epResume = remember(epKey) { state.library.resume(epKey) }
                Row(
                    Modifier.fillMaxWidth().clickable { state.playEpisode(item, ep, startFromBeginning = false) }.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!ep.posterUrl.isNullOrBlank()) {
                        Box(Modifier.size(width = 140.dp, height = 80.dp).clip(RoundedCornerShape(6.dp))) {
                            AsyncImage(
                                model = ep.posterUrl,
                                contentDescription = ep.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                            if (epResume != null && epResume.durationMs > 0) {
                                val prog = (epResume.positionMs.toFloat() / epResume.durationMs).coerceIn(0.02f, 1f)
                                Box(
                                    Modifier
                                        .align(Alignment.BottomStart)
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .background(Color(0x66000000)),
                                ) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth(prog)
                                            .fillMaxHeight()
                                            .background(dev.csdesktop.ui.theme.CsPurple),
                                    )
                                }
                            }
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            buildString {
                                row.group?.let { append("$it · ") }
                                append(episodeHeading(ep))
                            },
                            style = MaterialTheme.typography.titleSmall,
                        )
                        if (!ep.description.isNullOrBlank()) {
                            Text(
                                ep.description!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (epResume != null && epResume.positionMs > 2_000) {
                        Column(horizontalAlignment = Alignment.End) {
                            Button(onClick = { state.playEpisode(item, ep, startFromBeginning = false) }) {
                                Text("Resume (${formatTime(epResume.positionMs / 1000.0)})")
                            }
                            androidx.compose.material3.TextButton(onClick = { state.playEpisode(item, ep, startFromBeginning = true) }) {
                                Text("From start", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    } else {
                        Button(onClick = { state.playEpisode(item, ep, startFromBeginning = false) }) { Text("Play") }
                    }
                }
            }
        }
        val recs = item.recommendations.orEmpty()
        if (recs.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                Text("More like this", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(recs.take(20)) { rec ->
                        Column(
                            Modifier.width(120.dp).clickable {
                                state.openResult(rec.apiName, rec.url, rec.name)
                            },
                        ) {
                            AsyncImage(
                                model = rec.posterUrl,
                                contentDescription = rec.name,
                                modifier = Modifier.height(170.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop,
                            )
                            Text(rec.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

private data class EpisodeRow(val episode: Episode, val group: String?)

private fun episodeHeading(ep: Episode): String {
    val code = buildString {
        ep.season?.let { append("S$it") }
        ep.episode?.let { append("E$it") }
    }
    val name = ep.name?.trim()?.takeIf { it.isNotBlank() }
    return when {
        code.isNotEmpty() && name != null && !name.contains(code, ignoreCase = true) -> "$code · $name"
        code.isNotEmpty() -> code
        name != null -> name
        else -> "Episode"
    }
}

@Composable
fun TextButtonBack(onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick, modifier = Modifier.padding(horizontal = 8.dp)) { Text("← Back") }
}

@Composable
fun AssistPlain(text: String) {
    Text(
        text,
        modifier = Modifier.padding(end = 8.dp),
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge,
    )
}

private fun formatTime(seconds: Double): String {
    val s = seconds.toInt().coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
