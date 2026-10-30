package com.ptvin.tmdbrows.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.ptvin.tmdbrows.R
import com.ptvin.tmdbrows.channel.ChannelRefresher
import com.ptvin.tmdbrows.data.PreviewCache
import com.ptvin.tmdbrows.data.RowConfig
import com.ptvin.tmdbrows.data.Rows
import com.ptvin.tmdbrows.data.Tmdb
import com.ptvin.tmdbrows.data.TmdbItem
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onNew: () -> Unit,
    onEdit: (RowConfig) -> Unit,
    onSettings: () -> Unit,
    onHelp: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rows by Rows.items.collectAsState()
    var refreshing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val published = rows.count { it.lastPublishedAt != null && it.lastError == null }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 56.dp, vertical = 32.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = pluralStringResource(R.plurals.home_rows, rows.size, rows.size, published),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TvButton(onClick = onNew) { Text(stringResource(R.string.home_new_row)) }
            Spacer(Modifier.width(12.dp))
            TvOutlinedButton(
                onClick = {
                    refreshing = true
                    scope.launch {
                        status = ChannelRefresher.refreshAll(context).message
                        refreshing = false
                    }
                },
                enabled = !refreshing,
            ) {
                Text(stringResource(if (refreshing) R.string.home_refreshing else R.string.home_refresh))
            }
            Spacer(Modifier.width(12.dp))
            TvOutlinedButton(onClick = onSettings) { Text(stringResource(R.string.home_settings)) }
            Spacer(Modifier.width(12.dp))
            TvOutlinedButton(onClick = onHelp) { Text(stringResource(R.string.home_help)) }
        }

        StatusLine(status)
        Spacer(Modifier.height(16.dp))

        if (rows.isEmpty()) {
            Text(
                stringResource(R.string.home_empty),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(rows, key = { it.id }) { row ->
                    RowTile(row, onClick = { onEdit(row) })
                }
            }
        }
    }
}

@Composable
private fun RowTile(row: RowConfig, onClick: () -> Unit) {
    val context = LocalContext.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.03f else 1f, label = "tileScale")
    val borderColor by animateColorAsState(
        targetValue = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.borderVariant,
        label = "tileBorder",
    )
    var preview by remember(row.id) { mutableStateOf<List<TmdbItem>>(emptyList()) }
    val artLanguage = posterSourceLanguage()

    LaunchedEffect(row) {
        preview = runCatching { PreviewCache.load(context, row, 3) }.getOrDefault(emptyList())
    }

    Surface(
        onClick = onClick,
        interactionSource = interaction,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedContentColor = MaterialTheme.colorScheme.onSurface,
            pressedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            pressedContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, borderColor)),
            focusedBorder = Border(BorderStroke(2.dp, borderColor)),
        ),
    ) {
        Column(Modifier.clip(RoundedCornerShape(16.dp))) {
            Row(Modifier.fillMaxWidth().height(108.dp)) {
                repeat(3) { slot ->
                    val item = preview.getOrNull(slot)
                    if (item == null) {
                        SkeletonFill(Modifier.weight(1f).fillMaxHeight())
                    } else {
                        SkeletonImage(
                            url = Tmdb.artUrl(row, item, preview = true, language = artLanguage, small = true),
                            contentDescription = item.displayTitle,
                            fallbackUrl = if (artLanguage != null) {
                                Tmdb.artUrl(row, item, preview = true, small = true)
                            } else {
                                null
                            },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = row.title.ifBlank { row.defaultTitle(context) },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = row.lastError?.let { context.getString(R.string.status_error, it) }
                        ?: row.summary(context),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (row.lastError != null) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
