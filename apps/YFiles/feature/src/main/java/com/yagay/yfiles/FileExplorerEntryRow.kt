package com.yagay.yfiles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** FileExplorer-inspired file row; no filesystem IO runs during composition. */
@Composable
internal fun FileExplorerEntryRow(
    entry: FileEntry,
    subtitle: String,
    detail: String?,
    enabled: Boolean,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = 12.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.size(40.dp),
        ) {
            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                val icon = when (explorerIconCategory(entry.name, entry.isDirectory)) {
                    ExplorerIconCategory.FOLDER -> Icons.Default.Folder
                    ExplorerIconCategory.IMAGE -> Icons.Default.Image
                    ExplorerIconCategory.VIDEO -> Icons.Default.Movie
                    ExplorerIconCategory.AUDIO -> Icons.Default.MusicNote
                    ExplorerIconCategory.DOCUMENT -> Icons.Default.Description
                    ExplorerIconCategory.OTHER -> Icons.Default.InsertDriveFile
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(23.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!detail.isNullOrBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing()
    }
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        thickness = 0.5.dp,
    )
}

internal enum class ExplorerIconCategory { FOLDER, IMAGE, VIDEO, AUDIO, DOCUMENT, OTHER }

internal fun explorerIconCategory(name: String, isDirectory: Boolean): ExplorerIconCategory {
    if (isDirectory) return ExplorerIconCategory.FOLDER
    return when (name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg", "png", "webp", "gif", "heic" -> ExplorerIconCategory.IMAGE
        "mp4", "mkv", "mov", "avi", "webm" -> ExplorerIconCategory.VIDEO
        "mp3", "flac", "wav", "ogg", "m4a" -> ExplorerIconCategory.AUDIO
        "txt", "pdf", "doc", "docx", "md" -> ExplorerIconCategory.DOCUMENT
        else -> ExplorerIconCategory.OTHER
    }
}
