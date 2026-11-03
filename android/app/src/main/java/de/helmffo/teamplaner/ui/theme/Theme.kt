package de.helmffo.teamplaner.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Hell = lightColorScheme(
    primary = Color(0xFF1B5E20),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA5D6A7),
    onPrimaryContainer = Color(0xFF002106),
    secondary = Color(0xFF52634F),
    tertiary = Color(0xFF39656B),
)

private val Dunkel = darkColorScheme(
    primary = Color(0xFF8BD68E),
    onPrimary = Color(0xFF00390F),
    primaryContainer = Color(0xFF1B5E20),
    onPrimaryContainer = Color(0xFFA5D6A7),
    secondary = Color(0xFFB9CCB4),
    tertiary = Color(0xFFA1CED5),
)

@Composable
fun TeamplanerTheme(dunkel: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val farben = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val kontext = LocalContext.current
            if (dunkel) dynamicDarkColorScheme(kontext) else dynamicLightColorScheme(kontext)
        }
        dunkel -> Dunkel
        else -> Hell
    }
    MaterialTheme(colorScheme = farben, content = content)
}
