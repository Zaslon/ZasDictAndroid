package com.zaslon.zasdict.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.zaslon.zasdict.R
import com.zaslon.zasdict.ui.theme.ZasTheme
import com.zaslon.zasdict.data.GitHubApiClient
import com.zaslon.zasdict.ui.theme.einkFocusFrame

@Composable
fun GitHubFileBrowserDialog(
    entries: List<GitHubApiClient.FileEntry>,
    currentPath: String,
    repoLabel: String,
    isLoading: Boolean,
    error: String?,
    fileFilter: (String) -> Boolean = { true },
    onSelect: (path: String, name: String) -> Unit,
    onDismiss: () -> Unit,
    onNavigate: (path: String) -> Unit,
    onNavigateUp: () -> Unit,
    onSelectFolder: ((path: String) -> Unit)? = null
) {
    val title = if (currentPath.isEmpty()) repoLabel else currentPath.substringAfterLast("/")

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.einkFocusFrame(),
        title = { DialogTitle(title) },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
            ) {
                when {
                    isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    error != null -> SelectionContainer {
                        Text(
                            text = error,
                            color = ZasTheme.colors.errorText,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                        if (currentPath.isNotEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onNavigateUp() }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painterResource(R.drawable.subdirectory_arrow_right_rotated),
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("上へ")
                                }
                                Divider()
                            }
                        }
                        items(entries) { entry ->
                            val selectable = !entry.isFolder && fileFilter(entry.name)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (entry.isFolder) onNavigate(entry.path)
                                        else if (selectable) onSelect(entry.path, entry.name)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = if (entry.isFolder) painterResource(R.drawable.folder)
                                              else painterResource(R.drawable.draft),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = when {
                                        entry.isFolder -> ZasTheme.colors.accent
                                        selectable -> MaterialTheme.colorScheme.onSurface
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = entry.name,
                                        color = if (!entry.isFolder && !selectable)
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (!entry.isFolder && entry.size > 0) {
                                        Text(
                                            text = formatGitHubSize(entry.size),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            Divider()
                        }
                        if (entries.isEmpty() && !isLoading) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "このフォルダは空です",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (onSelectFolder != null) {
                ZasTextButton(onClick = { onSelectFolder(currentPath) }) {
                    Text("このフォルダを保存先にする")
                }
            }
        },
        dismissButton = {
            ZasTextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )
}

private fun formatGitHubSize(bytes: Long): String = when {
    bytes < 1024 -> "${bytes} B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
}
