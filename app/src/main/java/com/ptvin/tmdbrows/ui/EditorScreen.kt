@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.ptvin.tmdbrows.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ptvin.tmdbrows.R
import com.ptvin.tmdbrows.channel.ChannelPublisher
import com.ptvin.tmdbrows.channel.ChannelRefresher
import com.ptvin.tmdbrows.data.ArtStyle
import com.ptvin.tmdbrows.data.Genres
import com.ptvin.tmdbrows.data.MediaType
import com.ptvin.tmdbrows.data.Opener
import com.ptvin.tmdbrows.data.Prefs
import com.ptvin.tmdbrows.data.Providers
import com.ptvin.tmdbrows.data.RowConfig
import com.ptvin.tmdbrows.data.RowIdInput
import com.ptvin.tmdbrows.data.RowItem
import com.ptvin.tmdbrows.data.RowMode
import com.ptvin.tmdbrows.data.Rows
import com.ptvin.tmdbrows.data.Section
import com.ptvin.tmdbrows.data.Tmdb
import com.ptvin.tmdbrows.data.TmdbItem
import com.ptvin.tmdbrows.data.TmdbProvider
import com.ptvin.tmdbrows.data.deviceRegion
import com.ptvin.tmdbrows.data.label
import com.ptvin.tmdbrows.data.shortLabel
import com.ptvin.tmdbrows.data.sortLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun EditorScreen(initial: RowConfig?, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val key = remember { Prefs.tmdbKey(context) }
    val language = remember { Prefs.posterLanguage(context) }
    var draft by remember { mutableStateOf(initial ?: RowConfig()) }
    var previewItems by remember { mutableStateOf<List<TmdbItem>>(emptyList()) }
    var previewError by remember { mutableStateOf<String?>(null) }
    var previewLoading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var deleteArmed by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(false) }
    var providers by remember { mutableStateOf<List<TmdbProvider>>(emptyList()) }
    var providerQuery by remember { mutableStateOf("") }
    var idInput by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<TmdbItem>>(emptyList()) }
    var searchNote by remember { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(false) }

    LaunchedEffect(
        draft.mode,
        draft.mediaType,
        draft.section,
        draft.genreIds,
        draft.minRating,
        draft.yearFrom,
        draft.sortBy,
        draft.art,
        draft.providers,
    ) {
        if (draft.mode == RowMode.IDS) return@LaunchedEffect
        previewLoading = true
        previewError = null
        delay(450)
        runCatching { withContext(Dispatchers.IO) { Tmdb.fetchItems(context, key, draft, 12, language) } }
            .onSuccess {
                previewItems = it
                if (it.isEmpty()) previewError = context.getString(R.string.error_no_results)
            }
            .onFailure {
                previewItems = emptyList()
                previewError = it.message
            }
        previewLoading = false
    }

    LaunchedEffect(draft.mediaType, draft.mode, key) {
        if (draft.mode != RowMode.PLATFORM) return@LaunchedEffect
        providers = withContext(Dispatchers.IO) {
            Tmdb.providers(context, key, draft.mediaType, deviceRegion())
        }
    }

    fun save() {
        if (draft.mode == RowMode.PLATFORM && draft.providers.isEmpty()) {
            status = context.getString(R.string.error_provider_empty)
            return
        }
        if (draft.mode == RowMode.IDS && draft.items.isEmpty()) {
            status = context.getString(R.string.error_ids_empty)
            return
        }
        val row = draft.copy(title = draft.title.ifBlank { draft.defaultTitle(context) }, lastError = null)
        saving = true
        scope.launch {
            val result = ChannelRefresher.publish(context, row)
            saving = false
            if (result.lastError == null) {
                onDone()
            } else {
                draft = result
                status = result.lastError
            }
        }
    }

    fun addById() {
        val entries = RowIdInput.parse(idInput)
        if (entries.isEmpty()) {
            status = context.getString(R.string.error_ids_empty)
            return
        }
        adding = true
        status = null
        scope.launch {
            try {
                val filled = withContext(Dispatchers.IO) {
                    entries.mapNotNull { Tmdb.fillEntry(context, key, it, language) }
                }
                if (filled.isEmpty()) {
                    status = context.getString(R.string.error_ids_none)
                } else {
                    draft = draft.copy(items = (draft.items + filled).distinctBy { it.id }.take(50))
                    idInput = ""
                }
            } catch (error: Exception) {
                status = error.message
            } finally {
                adding = false
            }
        }
    }

    fun search() {
        val query = searchQuery.trim()
        if (query.isEmpty()) return
        searching = true
        searchNote = null
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { Tmdb.search(context, key, query, language) } }
                .onSuccess {
                    searchResults = it
                    if (it.isEmpty()) searchNote = context.getString(R.string.search_empty)
                }
                .onFailure {
                    searchResults = emptyList()
                    searchNote = it.message
                }
            searching = false
        }
    }

    fun addResult(item: TmdbItem) {
        val type = item.type ?: return
        if (draft.items.any { it.id == item.id }) return
        draft = draft.copy(
            items = (draft.items + RowItem(item.id, type, item.displayTitle)).take(50),
        )
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(34.dp),
    ) {
        Column(Modifier.weight(0.46f).fillMaxHeight()) {
            Text(
                text = stringResource(if (initial == null) R.string.editor_title_new else R.string.editor_title_edit),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.editor_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 12.dp),
            ) {
                SectionLabel(stringResource(R.string.label_mode))
                SegmentedTabs(
                    options = RowMode.entries.map { it to it.shortLabel(context) },
                    selected = draft.mode,
                    onSelect = { draft = draft.copy(mode = it) },
                )

                Spacer(Modifier.height(10.dp))
                SectionLabel(stringResource(R.string.label_title))
                TvTextField(
                    value = draft.title,
                    onValueChange = { draft = draft.copy(title = it) },
                    placeholder = draft.defaultTitle(context),
                    hint = stringResource(R.string.text_field_hint),
                )

                if (draft.mode != RowMode.IDS) {
                    Spacer(Modifier.height(10.dp))
                    SectionLabel(stringResource(R.string.label_type))
                    ChipRow(
                        options = MediaType.entries.map { it to it.label(context) },
                        selected = draft.mediaType,
                        onSelect = { type ->
                            if (type != draft.mediaType) {
                                draft = draft.copy(
                                    mediaType = type,
                                    genreIds = emptyList(),
                                    section = if (draft.section in RowConfig.sectionsFor(type)) draft.section
                                    else Section.POPULAR,
                                )
                            }
                        },
                    )
                }

                when (draft.mode) {
                    RowMode.FILTER -> {
                        SectionLabel(stringResource(R.string.label_section))
                        ChipRow(
                            options = RowConfig.sectionsFor(draft.mediaType).map { it to it.label(context) },
                            selected = draft.section,
                            onSelect = { draft = draft.copy(section = it) },
                        )
                    }

                    RowMode.PLATFORM -> {
                        SectionLabel(stringResource(R.string.label_provider))
                        val found = if (providerQuery.isBlank()) {
                            providers.sortedBy { providerRank(it.name) }.take(MAIN_PROVIDERS)
                        } else {
                            providers
                                .filter { it.name.contains(providerQuery.trim(), ignoreCase = true) }
                                .take(12)
                        }
                        val providerList = buildList {
                            addAll(found)
                            draft.providers.firstOrNull()
                                ?.takeIf { id -> none { it.id == id } }
                                ?.let { add(TmdbProvider(it, Providers.name(it))) }
                        }
                        ProviderChips(
                            options = providerList,
                            selected = draft.providers.firstOrNull(),
                            onSelect = { draft = draft.copy(providers = listOf(it)) },
                        )
                        Spacer(Modifier.height(10.dp))
                        TvTextField(
                            value = providerQuery,
                            onValueChange = { providerQuery = it },
                            placeholder = stringResource(R.string.provider_search),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (draft.mediaType == MediaType.TV) {
                            Spacer(Modifier.height(10.dp))
                            SectionLabel(stringResource(R.string.label_section))
                            ChipRow(
                                options = PLATFORM_SECTIONS.map { it to it.label(context) },
                                selected = draft.section,
                                onSelect = { draft = draft.copy(section = it) },
                            )
                        }
                    }

                    RowMode.IDS -> Unit
                }

                if (draft.mode != RowMode.IDS) {
                    Spacer(Modifier.height(8.dp))
                    Stepper(
                        label = stringResource(R.string.label_items),
                        value = draft.itemCount.toString(),
                        onMinus = { draft = draft.copy(itemCount = (draft.itemCount - 10).coerceAtLeast(10)) },
                        onPlus = { draft = draft.copy(itemCount = (draft.itemCount + 10).coerceAtMost(50)) },
                    )
                }

                Spacer(Modifier.height(10.dp))
                SectionLabel(stringResource(R.string.label_art))
                ChipRow(
                    options = ArtStyle.entries.map { it to it.label(context) },
                    selected = draft.art,
                    onSelect = { draft = draft.copy(art = it) },
                )

                if (draft.art == ArtStyle.BACKDROP) {
                    Spacer(Modifier.height(10.dp))
                    SwitchRow(
                        label = stringResource(R.string.cover_title),
                        checked = draft.showCover,
                        onCheckedChange = { draft = draft.copy(showCover = it) },
                        description = stringResource(R.string.cover_help),
                    )
                }

                Spacer(Modifier.height(10.dp))
                SectionLabel(stringResource(R.string.label_opener))
                ChipRow(
                    options = listOf<Pair<Opener?, String>>(
                        null to context.getString(R.string.opener_default, Prefs.opener(context).label),
                    ) + Opener.entries.map { it to it.label },
                    selected = draft.opener,
                    onSelect = { draft = draft.copy(opener = it) },
                )

                if (draft.mode != RowMode.IDS) {
                    Spacer(Modifier.height(12.dp))
                    TvOutlinedButton(onClick = { advanced = !advanced }) {
                        Text(
                            stringResource(R.string.editor_advanced) +
                                if (advanced) "  ⌃" else "  ⌄",
                        )
                    }
                    AnimatedVisibility(
                        visible = advanced,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        Column {
                            if (draft.mode == RowMode.PLATFORM || draft.section == Section.DISCOVER) {
                                SectionLabel(stringResource(R.string.label_sort))
                                ChipRow(
                                    options = RowConfig.sortsFor(draft.mediaType)
                                        .map { it to sortLabel(context, it) },
                                    selected = draft.sortBy,
                                    onSelect = { draft = draft.copy(sortBy = it) },
                                )
                            }

                            Spacer(Modifier.height(10.dp))
                            Stepper(
                                label = stringResource(R.string.label_min_rating),
                                value = if (draft.minRating == 0.0) stringResource(R.string.value_any)
                                else draft.minRating.toString().replace('.', ','),
                                onMinus = { draft = draft.copy(minRating = (draft.minRating - 0.5).coerceAtLeast(0.0)) },
                                onPlus = { draft = draft.copy(minRating = (draft.minRating + 0.5).coerceAtMost(9.0)) },
                            )

                            Spacer(Modifier.height(10.dp))
                            SectionLabel(stringResource(R.string.label_year))
                            ChipRow(
                                options = YEARS.map { it.first to it.second },
                                selected = draft.yearFrom ?: 0,
                                onSelect = { draft = draft.copy(yearFrom = it.takeIf { year -> year > 0 }) },
                            )

                            Spacer(Modifier.height(10.dp))
                            SectionLabel(stringResource(R.string.label_genres))
                            GenreChips(
                                genres = Genres.namesFor(context, draft.mediaType),
                                selected = draft.genreIds,
                                onToggle = { id ->
                                    draft = draft.copy(
                                        genreIds = if (id in draft.genreIds) draft.genreIds - id
                                        else draft.genreIds + id,
                                    )
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                StatusLine(status, error = true)
                Row(
                    modifier = Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    TvButton(onClick = { save() }, enabled = !saving) {
                        Text(stringResource(if (saving) R.string.action_publishing else R.string.action_save))
                    }
                    if (initial != null) {
                        TvOutlinedButton(
                            onClick = {
                                if (!deleteArmed) {
                                    deleteArmed = true
                                    status = context.getString(R.string.hint_delete_again)
                                } else {
                                    ChannelPublisher.delete(context, draft.id)
                                    Rows.delete(draft.id)
                                    onDone()
                                }
                            },
                            enabled = !saving,
                        ) {
                            Text(
                                stringResource(
                                    if (deleteArmed) R.string.action_confirm_delete else R.string.action_delete,
                                ),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }

        Panel(
            title = stringResource(if (draft.mode == RowMode.IDS) R.string.label_list else R.string.preview_title),
            modifier = Modifier
                .weight(0.54f)
                .fillMaxHeight(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
            ) {
                if (draft.mode == RowMode.IDS) {
                    if (draft.items.isEmpty()) {
                        Text(
                            stringResource(R.string.list_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            draft.items.forEach { item ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = item.title.ifBlank { "#${item.id}" },
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                        Text(
                                            text = item.type?.label(context).orEmpty(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    TvOutlinedButton(
                                        onClick = { draft = draft.copy(items = draft.items - item) },
                                    ) { Text(stringResource(R.string.action_remove)) }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(18.dp))
                    SectionLabel(stringResource(R.string.label_ids))
                    TvTextField(
                        value = idInput,
                        onValueChange = { idInput = it },
                        placeholder = stringResource(R.string.ids_placeholder),
                        modifier = Modifier.fillMaxWidth(),
                        hint = stringResource(R.string.ids_hint),
                    )
                    Spacer(Modifier.height(8.dp))
                    TvButton(onClick = { addById() }, enabled = !adding) {
                        Text(stringResource(if (adding) R.string.action_adding else R.string.action_add))
                    }

                    Spacer(Modifier.height(18.dp))
                    SectionLabel(stringResource(R.string.label_search))
                    TvTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = stringResource(R.string.search_placeholder),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    TvButton(onClick = { search() }, enabled = !searching) {
                        Text(stringResource(if (searching) R.string.preview_loading else R.string.action_search))
                    }

                    StatusLine(searchNote)
                    if (searchResults.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            searchResults.forEach { item ->
                                ListItem(
                                    selected = false,
                                    onClick = { addResult(item) },
                                    headlineContent = {
                                        Text(
                                            item.displayTitle.ifBlank {
                                                context.getString(R.string.untitled)
                                            },
                                        )
                                    },
                                    supportingContent = {
                                        Text(
                                            listOfNotNull(
                                                item.type?.label(context),
                                                item.year?.toString(),
                                            ).joinToString(" · "),
                                        )
                                    },
                                )
                            }
                        }
                    } else if (searchNote == null) {
                        Text(
                            stringResource(R.string.search_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Text(
                        text = when {
                            previewLoading -> stringResource(R.string.preview_loading)
                            previewError != null -> previewError.orEmpty()
                            else -> stringResource(
                                R.string.preview_count,
                                previewItems.size,
                                draft.art.label(context),
                            )
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (previewError != null) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(18.dp))
                    if (previewItems.isNotEmpty()) {
                        PreviewGrid(
                            items = previewItems,
                            row = draft,
                            columns = if (draft.art == ArtStyle.POSTER) 4 else 3,
                        )
                    }
                }
            }
        }
    }
}

private val YEARS = listOf(
    0 to "—",
    2020 to "2020+",
    2010 to "2010+",
    2000 to "2000+",
    1990 to "1990+",
)

/** Plataformas em destaque; o resto aparece pela pesquisa. */
private const val MAIN_PROVIDERS = 8

/** Nomes que o Monet espera ver primeiro (os ids do TMDB mudam de região para região). */
private val MAIN_NAMES = listOf(
    "netflix", "prime video", "disney", "apple tv", "max", "globoplay", "paramount", "crunchyroll",
)

private fun providerRank(name: String): Int {
    val lower = name.lowercase()
    val main = MAIN_NAMES.indexOfFirst { lower.contains(it) }
    if (main < 0) return 100
    // Variantes ("com anúncios", canais dentro de outra app) vão para o fim.
    val variant = listOf("with ads", "standard", "channel").any { lower.contains(it) }
    return if (variant) 90 + main else main * 10
}

/** Secções que fazem sentido com uma plataforma escolhida. */
private val PLATFORM_SECTIONS = listOf(
    Section.POPULAR,
    Section.ON_THE_AIR,
    Section.AIRING_TODAY,
    Section.NEW_SERIES,
)
