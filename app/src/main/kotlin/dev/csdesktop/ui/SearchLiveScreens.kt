package dev.csdesktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.TvType
import dev.csdesktop.AppState
import dev.csdesktop.Destination

@Composable
fun SearchScreen(state: AppState) {
    val query by state.searchQuery.collectAsState()
    val hits by state.searchHits.collectAsState()
    val loading by state.searchLoading.collectAsState()
    val type by state.typeFilter.collectAsState()
    val providerFilter by state.providerFilter.collectAsState()
    val plugins by state.plugins.collectAsState()
    val generation by state.pluginGeneration.collectAsState()
    val allProviders = remember(plugins, generation) { state.allEnabledProviders().map { it.name }.distinct() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Search", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { state.search(it, providerFilter) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(if (providerFilter != null) "Search in $providerFilter…" else "Search across all installed extensions…") },
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        if (allProviders.size > 1) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = providerFilter == null,
                        onClick = { state.setProviderFilter(null) },
                        label = { Text("All Extensions (${allProviders.size})") },
                    )
                }
                items(allProviders) { name: String ->
                    FilterChip(
                        selected = providerFilter == name,
                        onClick = { state.setProviderFilter(if (providerFilter == name) null else name) },
                        label = { Text(name) },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(selected = type == null, onClick = { state.setTypeFilter(null) }, label = { Text("All Types") }) }
            items(TvType.entries) { t ->
                FilterChip(selected = type == t, onClick = { state.setTypeFilter(if (type == t) null else t) }, label = { Text(t.name) })
            }
        }
        Spacer(Modifier.height(12.dp))
        when {
            loading -> CircularProgressIndicator()
            query.isBlank() -> Text("Search across all loaded providers. Results appear dynamically.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            hits.isEmpty() -> Text("No results for \"$query\".", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(120.dp),
                contentPadding = PaddingValues(4.dp),
            ) {
                gridItems(hits) { hit ->
                    val badgeText = if (hit.item.type == TvType.Live) "LIVE" else hit.provider
                    PosterCard(hit.item.name, hit.item.posterUrl, badge = badgeText) {
                        state.openResult(hit.item.apiName, hit.item.url, hit.item.name)
                    }
                }
            }
        }
    }
}

@Composable
fun LiveTvScreen(state: AppState) {
    val hits by state.live.collectAsState()
    val loading by state.liveLoading.collectAsState()
    val grouped = hits.groupBy { it.provider }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Live TV", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text("Channels from installed providers that expose TvType.Live.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        when {
            loading -> CircularProgressIndicator()
            hits.isEmpty() -> EmptyState(
                title = "No live sources",
                body = "Install a live plugin such as iptv-org from the official extensions repository.",
                action = "Open Extensions",
                onAction = { state.go(Destination.Extensions) },
            )
            else -> androidx.compose.foundation.lazy.LazyColumn {
                grouped.forEach { (provider, list) ->
                    item { Text(provider, style = MaterialTheme.typography.titleLarge) }
                    item {
                        LazyRow {
                            items(list) { hit ->
                                PosterCard(hit.item.name, hit.item.posterUrl, badge = "LIVE") {
                                    state.openResult(hit.item.apiName, hit.item.url, hit.item.name)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
