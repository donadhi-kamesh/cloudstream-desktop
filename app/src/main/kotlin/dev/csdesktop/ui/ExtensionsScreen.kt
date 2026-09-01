package dev.csdesktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.csdesktop.AppState
import dev.csdesktop.extloader.OfficialRepos
import dev.csdesktop.extloader.SavedRepository
import dev.csdesktop.extloader.SitePlugin

private const val TAB_ALL = "all"
private const val TAB_INSTALLED = "installed"

@Composable
fun ExtensionsScreen(state: AppState) {
    val installed by state.plugins.collectAsState()
    val repos by state.repositories.collectAsState()
    val catalog by state.catalogByRepo.collectAsState()
    val status by state.extStatus.collectAsState()
    val installing by state.installing.collectAsState()
    var url by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(TAB_ALL) }

    val q = query.trim()
    fun matches(name: String, extra: String = "") =
        q.isBlank() || name.contains(q, true) || extra.contains(q, true)

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Extensions", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Add a repository, then install extensions. Nothing is bundled.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                OfficialRepos.EXTENSIONS_REPO,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = { state.addRepository(OfficialRepos.EXTENSIONS_REPO) }) { Text("Add official repo") }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("repo.json URL, GitHub blob URL, plugins.json, or shortcode") },
                singleLine = true,
            )
            Button(onClick = { state.addRepository(url); url = "" }, enabled = url.isNotBlank()) { Text("Add repository") }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search extensions") },
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = tab == TAB_ALL, onClick = { tab = TAB_ALL }, label = { Text("All") })
            FilterChip(selected = tab == TAB_INSTALLED, onClick = { tab = TAB_INSTALLED }, label = { Text("Installed") })
            repos.forEach { repo ->
                FilterChip(
                    selected = tab == repo.url,
                    onClick = { tab = repo.url },
                    label = { Text(repo.name.ifBlank { repo.url }.take(24)) },
                )
            }
        }
        val currentStatus = status
        if (currentStatus != null) {
            val failed = currentStatus.contains("fail", ignoreCase = true) ||
                currentStatus.contains("could not", ignoreCase = true)
            Text(
                currentStatus,
                color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (tab == TAB_INSTALLED || tab == TAB_ALL) {
                val shown = installed.filter {
                    matches(it.displayName, it.authors.joinToString() + " " + it.description)
                }
                if (tab == TAB_INSTALLED) {
                    item { Text("Installed", style = MaterialTheme.typography.titleLarge) }
                    if (shown.isEmpty()) {
                        item {
                            Text("None installed.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else if (shown.isNotEmpty()) {
                    item { Text("Installed", style = MaterialTheme.typography.titleLarge) }
                }
                items(shown, key = { "inst-" + it.internalName }) { p ->
                    androidx.compose.material3.Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(p.displayName, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        buildString {
                                            append("v${p.version}")
                                            p.language?.let { append(" · $it") }
                                            if (p.authors.isNotEmpty()) append(" · ${p.authors.joinToString()}")
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(checked = p.enabled, onCheckedChange = { state.setPluginEnabled(p.internalName, it) })
                                Spacer(Modifier.width(8.dp))
                                if (p.enabled) {
                                    OutlinedButton(onClick = { state.openPluginSettings(p.internalName) }) { Text("Settings") }
                                    Spacer(Modifier.width(8.dp))
                                }
                                OutlinedButton(onClick = { state.uninstallPlugin(p.internalName) }) { Text("Uninstall") }
                            }
                            if (p.enabled) {
                                val loadError = state.pluginLoadError(p.internalName)
                                if (loadError != null) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "This extension failed to load: $loadError",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            val repoTabs = if (tab == TAB_ALL) repos else repos.filter { it.url == tab }
            repoTabs.forEach { repo ->
                val plugins = (catalog[repo.url] ?: emptyList()).filter {
                    matches(it.name.ifBlank { it.internalName }, it.description.orEmpty() + " " + it.authors.joinToString())
                }
                item(key = "repo-${repo.url}") {
                    RepoHeader(repo = repo, count = plugins.size, onRemove = { state.removeRepository(repo.url) })
                }
                if (plugins.isEmpty()) {
                    item(key = "empty-${repo.url}") {
                        Text(
                            if (q.isNotBlank()) "No extensions match “$q”." else "No extensions listed.",
                            modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(plugins, key = { "p-${repo.url}-${it.internalName.ifBlank { it.name }}" }) { plugin ->
                    PluginRow(
                        repo = repo,
                        plugin = plugin,
                        installed = installed.any { it.internalName == plugin.internalName },
                        busy = installing == plugin.internalName.ifBlank { plugin.name },
                        onInstall = { state.installPlugin(repo, plugin) },
                        onOpen = {
                            val name = plugin.internalName.ifBlank { plugin.name }
                            if (installed.any { it.internalName == plugin.internalName }) {
                                state.openPluginSettings(plugin.internalName.ifBlank { name })
                            } else {
                                state.installPlugin(repo, plugin)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RepoHeader(repo: SavedRepository, count: Int, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(repo.name.ifBlank { repo.url }, style = MaterialTheme.typography.titleMedium)
            Text(
                "$count extension${if (count == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = onRemove) { Text("Remove repo") }
    }
}

@Composable
private fun PluginRow(
    repo: SavedRepository,
    plugin: SitePlugin,
    installed: Boolean,
    busy: Boolean,
    onInstall: () -> Unit,
    onOpen: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, top = 4.dp, bottom = 4.dp).clickable(onClick = onOpen),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(plugin.name.ifBlank { plugin.internalName }, style = MaterialTheme.typography.titleSmall)
            Text(
                buildString {
                    append("v${plugin.version}")
                    plugin.language?.let { append(" · $it") }
                    val types = plugin.tvTypes.orEmpty()
                    if (types.isNotEmpty()) append(" · ${types.joinToString()}")
                    if (!plugin.description.isNullOrBlank()) append(" — ${plugin.description}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(
            onClick = { if (installed) onOpen() else onInstall() },
            enabled = (!installed && !busy) || installed,
        ) {
            Text(
                when {
                    installed -> "Settings"
                    busy -> "Installing…"
                    else -> "Install"
                }
            )
        }
    }
}
