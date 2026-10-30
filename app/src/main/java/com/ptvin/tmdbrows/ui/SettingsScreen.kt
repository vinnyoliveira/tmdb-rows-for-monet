@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.ptvin.tmdbrows.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ptvin.tmdbrows.BuildConfig
import com.ptvin.tmdbrows.R
import com.ptvin.tmdbrows.channel.ChannelRefresher
import com.ptvin.tmdbrows.channel.RefreshScheduler
import com.ptvin.tmdbrows.data.Opener
import com.ptvin.tmdbrows.data.Prefs
import com.ptvin.tmdbrows.data.Tmdb
import com.ptvin.tmdbrows.data.TmdbLanguage
import com.ptvin.tmdbrows.data.deviceLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(onBack: () -> Unit, onHelp: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var keyDraft by remember { mutableStateOf(Prefs.tmdbKey(context)) }
    var opener by remember { mutableStateOf(Prefs.opener(context)) }
    var hours by remember { mutableStateOf(Prefs.refreshHours(context)) }
    var autoSync by remember { mutableStateOf(Prefs.autoSync(context)) }
    var language by remember { mutableStateOf(Prefs.languageChoice(context)) }
    var appLanguage by remember { mutableStateOf(Prefs.appLanguage(context)) }
    var betterPosters by remember { mutableStateOf(Prefs.betterPosters(context)) }
    var cardTitle by remember { mutableStateOf(Prefs.cardTitle(context)) }
    var cardTags by remember { mutableStateOf(Prefs.cardTags(context)) }
    var badgeNew by remember { mutableStateOf(Prefs.badgeNew(context)) }
    var badgeEpisodes by remember { mutableStateOf(Prefs.badgeEpisodes(context)) }
    var languages by remember { mutableStateOf<List<TmdbLanguage>>(emptyList()) }
    var showAllLanguages by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        languages = withContext(Dispatchers.IO) { Tmdb.languages(context, Prefs.tmdbKey(context)) }
    }

    val autoLanguage = deviceLanguage()
    val autoBase = autoLanguage.substringBefore('-')
    val autoName = (
        languages.firstOrNull { it.code.equals(autoLanguage, ignoreCase = true) }
            ?: languages.firstOrNull { it.code.equals(autoBase, ignoreCase = true) }
        )?.label ?: autoLanguage
    val visibleLanguages = if (showAllLanguages) {
        languages
    } else {
        languages.filter { it.code.substringBefore('-') in COMMON_LANGUAGES || it.code == language }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 56.dp, vertical = 30.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(
                stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 14.dp),
            )
            TvOutlinedButton(onClick = onHelp) { Text(stringResource(R.string.home_help)) }
        }

        Panel(stringResource(R.string.panel_key)) {
            Text(
                text = stringResource(R.string.key_help),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            TvTextField(
                value = keyDraft,
                onValueChange = { keyDraft = it },
                placeholder = stringResource(R.string.setup_key_placeholder),
                modifier = Modifier.width(860.dp),
            )
            Spacer(Modifier.height(14.dp))
            TvButton(
                onClick = {
                    busy = true
                    scope.launch {
                        Prefs.setTmdbKey(context, keyDraft)
                        val ok = withContext(Dispatchers.IO) { Tmdb.validate(context, keyDraft.trim()) }
                        busy = false
                        status = context.getString(
                            if (ok) R.string.key_saved else R.string.key_saved_invalid,
                        )
                    }
                },
                enabled = !busy,
            ) { Text(stringResource(if (busy) R.string.action_validating else R.string.action_save_key)) }
        }

        Panel(stringResource(R.string.app_language_title)) {
            Text(
                text = stringResource(R.string.app_language_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            AppLanguageChips(appLanguage) { code ->
                appLanguage = code
                Prefs.setAppLanguage(context, code)
                applyAppLanguage(context)
            }
        }

        Panel(stringResource(R.string.panel_language)) {
            Text(
                text = stringResource(R.string.language_help),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            ChipRow(
                options = listOf("" to context.getString(R.string.language_auto, autoName)) +
                    visibleLanguages.map { it.code to it.label },
                selected = language,
                onSelect = {
                    language = it
                    Prefs.setLanguageChoice(context, it)
                    status = context.getString(R.string.language_saved)
                },
            )
            Spacer(Modifier.height(10.dp))
            TvOutlinedButton(onClick = { showAllLanguages = !showAllLanguages }) {
                Text(
                    stringResource(
                        if (showAllLanguages) R.string.language_show_less else R.string.language_show_all,
                    ),
                )
            }
        }

        Panel(stringResource(R.string.panel_opener)) {
            ChipRow(
                options = Opener.entries.map { it to it.label },
                selected = opener,
                onSelect = {
                    opener = it
                    Prefs.setOpener(context, it)
                },
            )
        }

        Panel(stringResource(R.string.panel_art)) {
            Text(
                text = stringResource(R.string.better_posters_help),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            SwitchRow(
                label = stringResource(R.string.better_posters_title),
                checked = betterPosters,
                onCheckedChange = {
                    betterPosters = it
                    Prefs.setBetterPosters(context, it)
                },
                description = stringResource(R.string.better_posters_note),
            )
        }

        Panel(stringResource(R.string.panel_cards)) {
            SwitchRow(
                label = stringResource(R.string.card_title),
                checked = cardTitle,
                onCheckedChange = {
                    cardTitle = it
                    Prefs.setCardTitle(context, it)
                },
                description = stringResource(R.string.card_title_hint),
            )
            SwitchRow(
                label = stringResource(R.string.card_tags),
                checked = cardTags,
                onCheckedChange = {
                    cardTags = it
                    Prefs.setCardTags(context, it)
                },
                description = stringResource(R.string.card_tags_hint),
            )
            SwitchRow(
                label = stringResource(R.string.card_badge_new),
                checked = badgeNew,
                onCheckedChange = {
                    badgeNew = it
                    Prefs.setBadgeNew(context, it)
                },
                description = stringResource(R.string.card_badge_new_hint),
            )
            SwitchRow(
                label = stringResource(R.string.card_badge_episodes),
                checked = badgeEpisodes,
                onCheckedChange = {
                    badgeEpisodes = it
                    Prefs.setBadgeEpisodes(context, it)
                },
                description = stringResource(R.string.card_badge_episodes_hint),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.cards_apply_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Panel(stringResource(R.string.panel_refresh)) {
            SwitchRow(
                label = stringResource(R.string.sync_title),
                checked = autoSync,
                onCheckedChange = {
                    autoSync = it
                    Prefs.setAutoSync(context, it)
                    RefreshScheduler.ensure(context, hours, it)
                    status = null
                },
                description = stringResource(R.string.sync_help),
            )
            if (autoSync) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.refresh_every),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(10.dp))
                ChipRow(
                    options = listOf(6 to "6 h", 12 to "12 h", 24 to "24 h"),
                    selected = hours,
                    onSelect = {
                        hours = it
                        Prefs.setRefreshHours(context, it)
                        RefreshScheduler.ensure(context, it, enabled = true)
                    },
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TvButton(
                    onClick = {
                        busy = true
                        scope.launch {
                            status = ChannelRefresher.refreshAll(context).message
                            busy = false
                        }
                    },
                    enabled = !busy,
                ) { Text(stringResource(if (busy) R.string.action_publishing else R.string.action_refresh_now)) }

                TvOutlinedButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
            }
            StatusLine(status)
        }

        Panel(stringResource(R.string.panel_about)) {
            Text(
                text = stringResource(R.string.about_text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

private val COMMON_LANGUAGES = setOf(
    "pt", "en", "es", "fr", "de", "it", "ja", "ko", "zh", "ru", "hi", "ar",
)
