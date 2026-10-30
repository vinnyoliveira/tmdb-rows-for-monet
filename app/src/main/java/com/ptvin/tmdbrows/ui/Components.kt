@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.ptvin.tmdbrows.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.FilterChip
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.OutlinedButtonDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.ptvin.tmdbrows.R
import com.ptvin.tmdbrows.data.ArtStyle
import com.ptvin.tmdbrows.data.Prefs
import com.ptvin.tmdbrows.data.RowConfig
import com.ptvin.tmdbrows.data.Tmdb
import com.ptvin.tmdbrows.data.TmdbItem
import com.ptvin.tmdbrows.data.TmdbProvider
import com.ptvin.tmdbrows.data.Update

/** Botão com foco na cor do tema (o padrão do tv-material fica branco). */
@Composable
fun TvButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            focusedContentColor = MaterialTheme.colorScheme.onPrimary,
            pressedContainerColor = MaterialTheme.colorScheme.primary,
            pressedContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        content = content,
    )
}

@Composable
fun TvOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = OutlinedButtonDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            focusedContentColor = MaterialTheme.colorScheme.onPrimary,
            pressedContainerColor = MaterialTheme.colorScheme.primary,
            pressedContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        content = content,
    )
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
    )
}

/** Caixa com título para agrupar ajustes. */
@Composable
fun Panel(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.borderVariant, shape)
            .padding(horizontal = 26.dp, vertical = 22.dp),
    ) {
        if (title != null) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        content()
    }
}

/** Etiqueta de estado do canal: publicada, pendente ou com erro. */
@Composable
fun StatusPill(text: String, error: Boolean = false) {
    val color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChipRow(
    options: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        options.forEach { (value, label) ->
            FilterChip(selected = value == selected, onClick = { onSelect(value) }) {
                Text(label)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenreChips(
    genres: List<Pair<Int, String>>,
    selected: List<Int>,
    onToggle: (Int) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        genres.forEach { (id, label) ->
            FilterChip(selected = id in selected, onClick = { onToggle(id) }) {
                Text(label)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProviderChips(
    options: List<TmdbProvider>,
    selected: Int?,
    onSelect: (Int) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        options.forEach { provider ->
            FilterChip(selected = provider.id == selected, onClick = { onSelect(provider.id) }) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    provider.logoUrl?.let { logo ->
                        AsyncImage(
                            model = logo,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(26.dp)
                                .clip(RoundedCornerShape(6.dp)),
                        )
                    }
                    Text(provider.name)
                }
            }
        }
    }
}

@Composable
fun Stepper(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        TvButton(onClick = onMinus) { Text("−") }
        Text(
            text = value,
            modifier = Modifier.width(84.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
        TvButton(onClick = onPlus) { Text("+") }
    }
}

@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val shape = RoundedCornerShape(12.dp)
    var focused by remember { mutableStateOf(false) }
    // Só abre o teclado depois de tocar no campo com o OK do controle.
    var editing by remember { mutableStateOf(false) }
    val border by animateColorAsState(
        targetValue = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.border,
        label = "fieldBorder",
    )

    Column(modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = border,
                    shape = shape,
                )
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            if (value.isEmpty()) {
                Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                readOnly = !editing,
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged {
                        focused = it.isFocused
                        if (!it.isFocused) {
                            editing = false
                            keyboard?.hide()
                        }
                    }
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when {
                            // OK entra em edição; antes disso o campo só recebe foco.
                            (event.key == Key.DirectionCenter || event.key == Key.Enter) && !editing -> {
                                editing = true
                                keyboard?.show()
                                true
                            }
                            event.key == Key.DirectionDown -> {
                                editing = false
                                keyboard?.hide()
                                focusManager.moveFocus(FocusDirection.Down)
                                true
                            }
                            event.key == Key.DirectionUp -> {
                                editing = false
                                keyboard?.hide()
                                focusManager.moveFocus(FocusDirection.Up)
                                true
                            }
                            else -> false
                        }
                    },
            )
        }
        if (hint != null || focused) {
            Text(
                text = hint ?: stringResource(
                    if (editing) R.string.text_field_editing else R.string.text_field_hint,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, start = 4.dp),
            )
        }
    }
}

/** Placeholder animado enquanto a imagem não chega. */
@Composable
fun SkeletonFill(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.22f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)))
}

/** Imagem com esqueleto por baixo: aparece até o Coil terminar. */
@Composable
fun SkeletonImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallbackUrl: String? = null,
) {
    // O cartaz do BetterPosters pode falhar: nesse caso cai para a arte do TMDB.
    var current by remember(url, fallbackUrl) { mutableStateOf(url) }
    Box(modifier) {
        SkeletonFill(Modifier.matchParentSize())
        AsyncImage(
            model = current,
            contentDescription = contentDescription,
            contentScale = contentScale,
            onState = { state ->
                if (state is AsyncImagePainter.State.Error) {
                    current = if (current == fallbackUrl) null else fallbackUrl
                }
            },
            modifier = Modifier.matchParentSize(),
        )
    }
}

/** Idioma dos cartazes BetterPosters, ou nulo quando a opção está desligada. */
@Composable
fun posterSourceLanguage(): String? {
    val context = LocalContext.current
    return if (Prefs.betterPosters(context)) Prefs.btttrLanguage(context) else null
}

/** Abas em pílula, ao estilo do guia de TV: a ativa fica preenchida. */
@Composable
fun <T> SegmentedTabs(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.borderVariant, shape)
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { (value, label) ->
            val active = value == selected
            Surface(
                onClick = { onSelect(value) },
                interactionSource = remember { MutableInteractionSource() },
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
                    contentColor = if (active) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    focusedContainerColor = if (active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    focusedContentColor = if (active) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    pressedContainerColor = MaterialTheme.colorScheme.primary,
                    pressedContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                border = ClickableSurfaceDefaults.border(
                    border = Border(BorderStroke(1.dp, Color.Transparent)),
                    focusedBorder = Border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary)),
                ),
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Linha de ajuste com interruptor, como nas definições do sistema. */
@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun PosterStrip(items: List<TmdbItem>, row: RowConfig, modifier: Modifier = Modifier) {
    val poster = row.art == ArtStyle.POSTER
    val language = posterSourceLanguage()
    LazyRow(
        modifier = modifier.height(if (poster) 132.dp else 84.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items, key = { it.id }) { item ->
            SkeletonImage(
                url = Tmdb.artUrl(row, item, preview = true, language = language),
                contentDescription = item.displayTitle,
                fallbackUrl = if (language != null) Tmdb.artUrl(row, item, preview = true) else null,
                modifier = Modifier
                    .width(if (poster) 88.dp else 149.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp)),
            )
        }
    }
}

/** Grade estática: a prévia não recebe foco, então não rola com o controle. */
@Composable
fun PreviewGrid(items: List<TmdbItem>, row: RowConfig, columns: Int) {
    val poster = row.art == ArtStyle.POSTER
    val language = posterSourceLanguage()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.chunked(columns).forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                line.forEach { item ->
                    Column(Modifier.weight(1f)) {
                        SkeletonImage(
                            url = Tmdb.artUrl(row, item, preview = true, language = language),
                            contentDescription = item.displayTitle,
                            fallbackUrl = if (language != null) Tmdb.artUrl(row, item, preview = true) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(if (poster) 2f / 3f else 16f / 9f)
                                .clip(RoundedCornerShape(10.dp)),
                        )
                        Text(
                            text = item.displayTitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp, start = 2.dp),
                        )
                    }
                }
                repeat(columns - line.size) {
                    Box(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun StatusLine(text: String?, error: Boolean = false) {
    AnimatedVisibility(
        visible = !text.isNullOrBlank(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Text(
            text = text.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** Idiomas da interface (rótulos escritos na própria língua). */
val APP_LANGUAGES = listOf(
    "pt" to "Português",
    "en" to "English",
    "es" to "Español",
    "fr" to "Français",
)

/** Recria a atividade para o novo idioma entrar em vigor. */
fun applyAppLanguage(context: android.content.Context) {
    (context as? android.app.Activity)?.recreate()
}

@Composable
fun AppLanguageChips(selected: String, onSelect: (String) -> Unit) {
    ChipRow(
        options = listOf("" to stringResource(R.string.app_language_system)) + APP_LANGUAGES,
        selected = selected,
        onSelect = onSelect,
    )
}

/**
 * Aviso de nova versão: descarrega o APK e abre o instalador do sistema.
 * Fecha com "Agora não" (fica ignorada até sair outra versão).
 */
@Composable
fun UpdateOverlay(
    update: Update,
    installing: Boolean,
    error: String?,
    onInstall: () -> Unit,
    onLater: () -> Unit,
) {
    val installFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { installFocus.requestFocus() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.76f))
            .padding(horizontal = 80.dp, vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Panel(modifier = Modifier.widthIn(max = 900.dp)) {
            Text(
                stringResource(R.string.update_title, update.version),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.update_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.update_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TvButton(
                    onClick = onInstall,
                    enabled = !installing,
                    modifier = Modifier.focusRequester(installFocus),
                ) {
                    Text(
                        stringResource(
                            if (installing) R.string.update_downloading else R.string.update_install,
                        ),
                    )
                }
                TvOutlinedButton(onClick = onLater, enabled = !installing) {
                    Text(stringResource(R.string.update_later))
                }
            }
            StatusLine(error, error = true)
        }
    }
}

/** Tutorial curto por cima do ecrã; abre pelo botão "?" e fecha com Voltar. */
@Composable
fun HelpOverlay(visible: Boolean, onClose: () -> Unit) {
    val closeFocus = remember { FocusRequester() }
    LaunchedEffect(visible) { if (visible) closeFocus.requestFocus() }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.94f),
        exit = fadeOut() + scaleOut(targetScale = 0.96f),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .padding(horizontal = 80.dp, vertical = 48.dp),
            contentAlignment = Alignment.Center,
        ) {
            Panel(modifier = Modifier.widthIn(max = 1000.dp)) {
                Text(
                    stringResource(R.string.help_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(14.dp))
                listOf(
                    R.string.help_step1,
                    R.string.help_step2,
                    R.string.help_step3,
                    R.string.help_step4,
                ).forEach { line ->
                    Text(
                        stringResource(line),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.help_tip),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(20.dp))
                TvButton(onClick = onClose, modifier = Modifier.focusRequester(closeFocus)) {
                    Text(stringResource(R.string.action_close))
                }
            }
        }
    }
}
