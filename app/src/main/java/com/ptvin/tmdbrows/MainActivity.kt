package com.ptvin.tmdbrows

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import com.ptvin.tmdbrows.channel.ChannelPublisher
import com.ptvin.tmdbrows.channel.ChannelRefresher
import com.ptvin.tmdbrows.data.Prefs
import com.ptvin.tmdbrows.data.RowConfig
import com.ptvin.tmdbrows.data.Rows
import com.ptvin.tmdbrows.ui.EditorScreen
import com.ptvin.tmdbrows.ui.HelpOverlay
import com.ptvin.tmdbrows.ui.HomeScreen
import com.ptvin.tmdbrows.ui.RowsTheme
import com.ptvin.tmdbrows.ui.SettingsScreen
import com.ptvin.tmdbrows.ui.SetupScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        val tag = Prefs.appLanguage(newBase)
        if (tag.isBlank()) {
            super.attachBaseContext(newBase)
            return
        }
        val locale = Locale.forLanguageTag(tag)
        val config = Configuration(newBase.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RowsTheme {
                App()
            }
        }

        lifecycleScope.launch {
            if (Prefs.tmdbKey(this@MainActivity).isNotBlank()) {
                val cleaned = withContext(Dispatchers.IO) {
                    // Uma vez: apaga canais de versões antigas (nomes/arte que já não batem).
                    val done = Prefs.migrated(this@MainActivity, "channels_v1")
                    if (!done) {
                        ChannelPublisher.deleteAll(this@MainActivity)
                        Prefs.setMigrated(this@MainActivity, "channels_v1")
                    }
                    ChannelPublisher.reconcile(this@MainActivity)
                    !done
                }
                // Primeira publicação acontece sempre; depois disso, só com a sincronização ligada.
                val firstPublish = Rows.current().any { it.lastPublishedAt == null }
                if (cleaned || firstPublish ||
                    (Prefs.autoSync(this@MainActivity) && ChannelRefresher.isStale())
                ) {
                    ChannelRefresher.refreshAll(this@MainActivity)
                }
            }
        }
    }
}

private enum class Screen { SETUP, HOME, EDITOR, SETTINGS }

@Composable
private fun App() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var screen by remember {
        mutableStateOf(if (Prefs.tmdbKey(context).isNotBlank()) Screen.HOME else Screen.SETUP)
    }
    var editing by remember { mutableStateOf<RowConfig?>(null) }
    var help by remember { mutableStateOf(false) }

    BackHandler(enabled = help) { help = false }
    BackHandler(enabled = !help && (screen == Screen.EDITOR || screen == Screen.SETTINGS)) {
        screen = Screen.HOME
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                (fadeIn(tween(200)) + slideInHorizontally(tween(200)) { it / 28 }) togetherWith
                    fadeOut(tween(140))
            },
            label = "screen",
        ) { target ->
            when (target) {
                Screen.SETUP -> SetupScreen(
                    onDone = {
                        screen = Screen.HOME
                        // Publica as linhas de exemplo com a chave que acabou de entrar.
                        scope.launch { ChannelRefresher.refreshAll(context) }
                    },
                )

                Screen.HOME -> HomeScreen(
                    onNew = {
                        editing = null
                        screen = Screen.EDITOR
                    },
                    onEdit = {
                        editing = it
                        screen = Screen.EDITOR
                    },
                    onSettings = { screen = Screen.SETTINGS },
                    onHelp = { help = true },
                )

                Screen.EDITOR -> EditorScreen(
                    initial = editing,
                    onDone = { screen = Screen.HOME },
                )

                Screen.SETTINGS -> SettingsScreen(
                    onBack = {
                        screen = if (Prefs.tmdbKey(context).isBlank()) Screen.SETUP else Screen.HOME
                    },
                    onHelp = { help = true },
                )
            }
        }

        HelpOverlay(visible = help, onClose = { help = false })
    }
}
