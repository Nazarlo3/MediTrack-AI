package com.example.meditrackai.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.meditrackai.R

@Composable
fun MediTrackShell(selected: Int, onSelect: (Int) -> Unit, onSettings: () -> Unit,
    onBrandTap: () -> Unit, content: @Composable () -> Unit) {
    val titles = listOf(stringResource(R.string.tab_analysis), stringResource(R.string.my_medications), stringResource(R.string.history_title))
    Scaffold(containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clickable(onClick = onBrandTap)) {
                        Text("MediTrack", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(titles.getOrElse(selected) { titles[1] }, style = MaterialTheme.typography.headlineSmall)
                    }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, stringResource(R.string.settings_title)) }
                }
            }
        }, bottomBar = {
            NavigationBar(windowInsets = WindowInsets(0, 0, 0, 0), containerColor = MaterialTheme.colorScheme.surface) {
                listOf(1, 0, 2).forEach { index ->
                    NavigationBarItem(selected = selected == index, onClick = { onSelect(index) },
                        icon = { Icon(when (index) { 1 -> Icons.Default.DateRange; 0 -> Icons.Default.Search; else -> Icons.Default.List }, null) },
                        label = { Text(titles[index], maxLines = 1) })
                }
            }
        }) { padding -> Box(Modifier.fillMaxSize().padding(padding)) { content() } }
}
