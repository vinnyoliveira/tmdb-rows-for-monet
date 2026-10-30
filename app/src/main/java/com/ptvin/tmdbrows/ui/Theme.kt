package com.ptvin.tmdbrows.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val Scheme = darkColorScheme(
    primary = Color(0xFFBCC8FF),
    onPrimary = Color(0xFF0E1C4F),
    primaryContainer = Color(0xFF2E4483),
    onPrimaryContainer = Color(0xFFE4E8FF),
    secondary = Color(0xFFC3CADB),
    onSecondary = Color(0xFF1C2433),
    secondaryContainer = Color(0xFF3F4759),
    onSecondaryContainer = Color(0xFFDFE4F5),
    // Fundo mais escuro e textos mais claros: contraste alto em painéis e cartões.
    background = Color(0xFF0A0C11),
    onBackground = Color(0xFFF0F1F6),
    surface = Color(0xFF171C28),
    onSurface = Color(0xFFF0F1F6),
    surfaceVariant = Color(0xFF222937),
    onSurfaceVariant = Color(0xFFC7CDDA),
    inverseSurface = Color(0xFFEDEEF3),
    inverseOnSurface = Color(0xFF23262E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF5C0006),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    border = Color(0xFF4A5265),
    borderVariant = Color(0xFF333A49),
)

@Composable
fun RowsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme) {
        // Sem isto o texto sem cor explícita sai a preto sobre o fundo escuro.
        CompositionLocalProvider(LocalContentColor provides Scheme.onBackground, content = content)
    }
}
