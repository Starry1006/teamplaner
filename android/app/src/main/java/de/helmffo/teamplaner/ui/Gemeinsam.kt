package de.helmffo.teamplaner.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.helmffo.teamplaner.TeamplanerApp
import de.helmffo.teamplaner.daten.Teamplaner
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** ViewModel-Fabrik mit Zugriff auf den [Teamplaner] der App. */
inline fun <reified VM : ViewModel> fabrik(crossinline erstellen: (Teamplaner) -> VM): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as TeamplanerApp
            erstellen(app.teamplaner)
        }
    }

@Composable
fun PasswortFeld(
    wert: String,
    aendern: (String) -> Unit,
    beschriftung: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Done,
) {
    var sichtbar by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = wert,
        onValueChange = aendern,
        label = { androidx.compose.material3.Text(beschriftung) },
        singleLine = true,
        visualTransformation = if (sichtbar) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        trailingIcon = {
            IconButton(onClick = { sichtbar = !sichtbar }) {
                Icon(
                    if (sichtbar) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (sichtbar) "Passwort verbergen" else "Passwort anzeigen",
                )
            }
        },
        modifier = modifier,
    )
}

private val DATUM = DateTimeFormatter.ofPattern("dd.MM.yyyy")

/** ISO-Zeitpunkt vom Server → „03.11.2026“ (lokale Zeitzone). */
fun datum(iso: String): String = try {
    OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).format(DATUM)
} catch (_: Exception) {
    iso.take(10)
}

const val PASSWORT_MIN = 8
