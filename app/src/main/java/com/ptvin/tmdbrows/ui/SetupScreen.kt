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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ptvin.tmdbrows.R
import com.ptvin.tmdbrows.data.Prefs
import com.ptvin.tmdbrows.data.Tmdb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Primeiro arranque: sem chave do TMDB nada é publicado, então pedimos antes de tudo. */
@Composable
fun SetupScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var keyDraft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var appLanguage by remember { mutableStateOf(Prefs.appLanguage(context)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 56.dp, vertical = 36.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            text = stringResource(R.string.setup_intro),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        SectionLabel(stringResource(R.string.app_language_title))
        AppLanguageChips(appLanguage) { code ->
            appLanguage = code
            Prefs.setAppLanguage(context, code)
            applyAppLanguage(context)
        }
        Spacer(Modifier.height(22.dp))

        Panel(stringResource(R.string.setup_key_title)) {
            Text(stringResource(R.string.setup_step1), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.setup_step2), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.setup_step3), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(16.dp))
            TvTextField(
                value = keyDraft,
                onValueChange = { keyDraft = it },
                placeholder = stringResource(R.string.setup_key_placeholder),
                modifier = Modifier.width(860.dp),
                hint = stringResource(R.string.text_field_hint),
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TvButton(
                    onClick = {
                        if (keyDraft.isNotBlank()) {
                            busy = true
                            error = null
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    Tmdb.validate(context, keyDraft.trim())
                                }
                                busy = false
                                if (ok) {
                                    Prefs.setTmdbKey(context, keyDraft)
                                    onDone()
                                } else {
                                    error = context.getString(R.string.setup_invalid)
                                }
                            }
                        }
                    },
                    enabled = !busy,
                ) {
                    Text(stringResource(if (busy) R.string.setup_validating else R.string.setup_validate))
                }
            }
            StatusLine(error, error = true)
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.setup_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.setup_next),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
