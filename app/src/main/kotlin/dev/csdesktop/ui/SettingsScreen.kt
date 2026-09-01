package dev.csdesktop.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.csdesktop.AppState
import java.io.File
import javax.swing.JFileChooser

@Composable
fun SettingsScreen(state: AppState) {
    val s = state.settingsStore.settings
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Text("Player", style = MaterialTheme.typography.titleLarge)
        Text("Default quality: ${s.defaultQuality}p")
        Slider(
            value = s.defaultQuality.toFloat(),
            onValueChange = { value ->
                val snapped = listOf(360, 480, 720, 1080, 1440, 2160)
                    .minBy { q -> kotlin.math.abs(q - value.toInt()) }
                state.updateSettings { it.copy(defaultQuality = snapped) }
            },
            valueRange = 360f..2160f,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Hardware decode", modifier = Modifier.weight(1f))
            Switch(s.hardwareDecode, onCheckedChange = { v -> state.updateSettings { it.copy(hardwareDecode = v) } })
        }
        Text("Skip intro/credits hint: ${s.skipSeconds}s (used as default skip amount)")
        Slider(
            value = s.skipSeconds.toFloat(),
            onValueChange = { v -> state.updateSettings { it.copy(skipSeconds = v.toInt()) } },
            valueRange = 10f..120f,
        )
        Spacer(Modifier.height(16.dp))
        Text("Interface", style = MaterialTheme.typography.titleLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Dark theme", modifier = Modifier.weight(1f))
            Switch(s.theme != "light", onCheckedChange = { v -> state.updateSettings { it.copy(theme = if (v) "dark" else "light") } })
        }
        OutlinedTextField(s.language, { v -> state.updateSettings { it.copy(language = v) } }, label = { Text("UI language tag") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Text("Accounts (paste tokens locally — this app does not scrape logins)", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(s.anilistToken, { v -> state.updateSettings { it.copy(anilistToken = v) } }, label = { Text("AniList token") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(s.malToken, { v -> state.updateSettings { it.copy(malToken = v) } }, label = { Text("MyAnimeList token") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(s.simklToken, { v -> state.updateSettings { it.copy(simklToken = v) } }, label = { Text("Simkl token") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(s.openSubtitlesKey, { v -> state.updateSettings { it.copy(openSubtitlesKey = v) } }, label = { Text("OpenSubtitles API key (optional)") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Text("Downloads", style = MaterialTheme.typography.titleLarge)
        Text(s.downloadFolder, style = MaterialTheme.typography.bodyMedium)
        Button(onClick = {
            val chooser = JFileChooser()
            chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                state.updateSettings { it.copy(downloadFolder = chooser.selectedFile.absolutePath) }
            }
        }) { Text("Choose download folder") }
        Spacer(Modifier.height(16.dp))
        Text("Logcat", style = MaterialTheme.typography.titleLarge)
        Text(dev.csdesktop.log.Logcat.file.absolutePath, style = MaterialTheme.typography.bodySmall)
        Button(onClick = { state.go(dev.csdesktop.Destination.Logcat) }) { Text("Open logcat") }
        Spacer(Modifier.height(16.dp))
        Text("Backup", style = MaterialTheme.typography.titleLarge)
        Row {
            Button(onClick = {
                val chooser = JFileChooser()
                chooser.selectedFile = File("cs-desktop-backup.zip")
                if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                    state.backup(chooser.selectedFile)
                }
            }) { Text("Backup plugins + library") }
            Spacer(Modifier.padding(8.dp))
            Button(onClick = {
                val chooser = JFileChooser()
                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    state.restore(chooser.selectedFile)
                }
            }) { Text("Restore") }
        }
        Spacer(Modifier.height(16.dp))
        Text("Casting", style = MaterialTheme.typography.titleLarge)
        Text(
            "Chromecast is not bundled. Use Play in browser / copy stream URL from the player. A local HTTP re-export would add little over that for desktop.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text("About", style = MaterialTheme.typography.titleLarge)
        Text("CloudStream Desktop (cs-desktop) 1.0.0")
        Text("A desktop port of CloudStream 3. Copyright (c) reCloudStream contributors and cs-desktop authors.")
        Text("Licensed under GNU GPL-3.0. This project is not affiliated with, endorsed by, or maintained by reCloudStream.")
        Text("No video sources are included. Do not create or use extensions that host copyrighted media.")
        Text("DRM: ClearKey is decrypted locally via mpv. Widevine and PlayReady use the OS Edge CDM through WebView2 + Shaka Player. Hardware L1 DRM does not work on desktop.")
    }
}
