package dev.csdesktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.csdesktop.AppState
import dev.csdesktop.Destination

@Composable
fun CsApp(state: AppState) {
    val dest by state.nav.collectAsState()
    val snack by state.snack.collectAsState()
    val snackHost = remember { SnackbarHostState() }
    LaunchedEffect(snack) {
        if (snack != null) {
            snackHost.showSnackbar(snack!!)
            state.snackConsumed()
        }
    }
    val inPlayer = dest is Destination.Player
    Scaffold(snackbarHost = { SnackbarHost(snackHost) }) { padding ->
        Row(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)) {
            if (!inPlayer) {
                NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                    NavItem("Home", Icons.Default.Home, dest is Destination.Home) { state.go(Destination.Home) }
                    NavItem("Search", Icons.Default.Search, dest is Destination.Search) { state.go(Destination.Search) }
                    NavItem("Library", Icons.Default.VideoLibrary, dest is Destination.Library) { state.go(Destination.Library) }
                    NavItem("Downloads", Icons.Default.Download, dest is Destination.Downloads) { state.go(Destination.Downloads) }
                    NavItem("Extensions", Icons.Default.Extension, dest is Destination.Extensions) { state.go(Destination.Extensions) }
                    NavItem("Settings", Icons.Default.Settings, dest is Destination.Settings) { state.go(Destination.Settings) }
                    NavItem("Logcat", Icons.Default.BugReport, dest is Destination.Logcat) { state.go(Destination.Logcat) }
                }
            }
            Box(Modifier.fillMaxSize().padding(if (inPlayer) 0.dp else 8.dp)) {
                when (val d = dest) {
                    Destination.Home, Destination.Live -> HomeScreen(state)
                    Destination.Search -> SearchScreen(state)
                    Destination.Library -> LibraryScreen(state)
                    Destination.Downloads -> DownloadsScreen(state)
                    Destination.Extensions -> ExtensionsScreen(state)
                    Destination.Settings -> SettingsScreen(state)
                    Destination.Logcat -> LogcatScreen()
                    is Destination.Result -> ResultScreen(state, d)
                    is Destination.Player -> PlayerScreen(state, d)
                }
                ExtensionSettingsOverlay()
            }
        }
    }
}

@Composable
private fun NavItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    NavigationRailItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) },
    )
}
