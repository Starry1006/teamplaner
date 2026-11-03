package de.helmffo.teamplaner.ui.profil

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import de.helmffo.teamplaner.daten.PasswortAendern
import de.helmffo.teamplaner.daten.ProfilAendern
import de.helmffo.teamplaner.daten.Teamplaner
import de.helmffo.teamplaner.daten.meldung
import de.helmffo.teamplaner.daten.versuche
import de.helmffo.teamplaner.ui.PASSWORT_MIN
import de.helmffo.teamplaner.ui.PasswortFeld
import de.helmffo.teamplaner.ui.fabrik
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfilZustand(
    val server: String = "",
    val email: String = "",
    val name: String = "",
    val gespeicherterName: String = "",
    val passwortAlt: String = "",
    val passwortNeu: String = "",
    val laeuft: Boolean = false,
    val meldung: String? = null,
)

class ProfilViewModel(private val teamplaner: Teamplaner) : ViewModel() {
    private val _zustand = MutableStateFlow(ProfilZustand())
    val zustand: StateFlow<ProfilZustand> = _zustand

    init {
        viewModelScope.launch {
            val server = teamplaner.sitzung.first()?.server.orEmpty()
            versuche { teamplaner.api().ich() }
                .onSuccess { ich ->
                    _zustand.update {
                        it.copy(server = server, email = ich.email, name = ich.name, gespeicherterName = ich.name)
                    }
                }
                .onFailure { e -> _zustand.update { it.copy(server = server, meldung = e.meldung()) } }
        }
    }

    fun aendern(f: (ProfilZustand) -> ProfilZustand) = _zustand.update(f)

    private fun ausfuehren(aktion: suspend () -> String) {
        if (_zustand.value.laeuft) return
        _zustand.update { it.copy(laeuft = true) }
        viewModelScope.launch {
            val meldung = versuche { aktion() }.getOrElse { it.meldung() }
            _zustand.update { it.copy(laeuft = false, meldung = meldung) }
        }
    }

    fun nameSpeichern() = ausfuehren {
        val ich = teamplaner.api().profilAendern(ProfilAendern(_zustand.value.name.trim()))
        _zustand.update { it.copy(name = ich.name, gespeicherterName = ich.name) }
        "Name gespeichert"
    }

    fun passwortSpeichern() {
        val z = _zustand.value
        if (z.passwortNeu.length < PASSWORT_MIN) {
            _zustand.update { it.copy(meldung = "Das neue Passwort muss mindestens $PASSWORT_MIN Zeichen haben.") }
            return
        }
        ausfuehren {
            teamplaner.api().passwortAendern(PasswortAendern(z.passwortAlt, z.passwortNeu))
            _zustand.update { it.copy(passwortAlt = "", passwortNeu = "") }
            "Passwort geändert – andere Geräte wurden abgemeldet."
        }
    }

    fun abmelden() {
        viewModelScope.launch { teamplaner.abmelden() }
    }

    fun meldungGezeigt() = _zustand.update { it.copy(meldung = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilScreen(
    zurueck: () -> Unit,
    vm: ProfilViewModel = viewModel(factory = fabrik { ProfilViewModel(it) }),
) {
    val z by vm.zustand.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(z.meldung) {
        z.meldung?.let {
            snackbar.showSnackbar(it)
            vm.meldungGezeigt()
        }
    }
    val feld = Modifier.fillMaxWidth()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profil") },
                navigationIcon = {
                    IconButton(onClick = zurueck) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { innen ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innen)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(z.email, style = MaterialTheme.typography.titleMedium)
            Text(
                "Server: ${z.server.removeSuffix("/")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = z.name,
                onValueChange = { v -> vm.aendern { it.copy(name = v) } },
                label = { Text("Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = feld,
            )
            Button(
                onClick = vm::nameSpeichern,
                enabled = !z.laeuft && z.name.isNotBlank() && z.name.trim() != z.gespeicherterName,
            ) { Text("Name speichern") }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Passwort ändern", style = MaterialTheme.typography.titleSmall)
            PasswortFeld(
                wert = z.passwortAlt,
                aendern = { v -> vm.aendern { it.copy(passwortAlt = v) } },
                beschriftung = "Bisheriges Passwort",
                imeAction = ImeAction.Next,
                modifier = feld,
            )
            PasswortFeld(
                wert = z.passwortNeu,
                aendern = { v -> vm.aendern { it.copy(passwortNeu = v) } },
                beschriftung = "Neues Passwort (mind. 8 Zeichen)",
                modifier = feld,
            )
            Button(
                onClick = vm::passwortSpeichern,
                enabled = !z.laeuft && z.passwortAlt.isNotEmpty() && z.passwortNeu.isNotEmpty(),
            ) { Text("Passwort ändern") }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            OutlinedButton(onClick = vm::abmelden, modifier = feld) { Text("Abmelden") }
        }
    }
}
