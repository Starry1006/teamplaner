package de.helmffo.teamplaner.ui.anmeldung

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.helmffo.teamplaner.daten.Teamplaner
import de.helmffo.teamplaner.daten.meldung
import de.helmffo.teamplaner.daten.versuche
import de.helmffo.teamplaner.ui.PASSWORT_MIN
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AnmeldungZustand(
    val registrieren: Boolean = false,
    val server: String = "",
    val code: String = "",
    val name: String = "",
    val email: String = "",
    val passwort: String = "",
    val laeuft: Boolean = false,
    val fehler: String? = null,
)

class AnmeldungViewModel(private val teamplaner: Teamplaner) : ViewModel() {
    private val _zustand = MutableStateFlow(AnmeldungZustand())
    val zustand: StateFlow<AnmeldungZustand> = _zustand

    init {
        viewModelScope.launch {
            val letzter = teamplaner.letzterServer.first()
            if (letzter != null) _zustand.update { if (it.server.isEmpty()) it.copy(server = letzter) else it }
        }
    }

    fun aendern(f: (AnmeldungZustand) -> AnmeldungZustand) = _zustand.update { f(it).copy(fehler = null) }

    fun absenden() {
        val z = _zustand.value
        if (z.laeuft) return
        val problem = when {
            z.server.isBlank() -> "Bitte Serveradresse eingeben."
            z.registrieren && z.code.isBlank() -> "Bitte Einladungscode eingeben."
            z.registrieren && z.name.isBlank() -> "Bitte Namen eingeben."
            z.email.isBlank() -> "Bitte E-Mail eingeben."
            z.registrieren && z.passwort.length < PASSWORT_MIN ->
                "Das Passwort muss mindestens $PASSWORT_MIN Zeichen haben."
            z.passwort.isEmpty() -> "Bitte Passwort eingeben."
            else -> null
        }
        if (problem != null) {
            _zustand.update { it.copy(fehler = problem) }
            return
        }
        _zustand.update { it.copy(laeuft = true, fehler = null) }
        viewModelScope.launch {
            // Bei Erfolg wechselt die App über den Sitzungs-Flow automatisch in den Hauptbereich.
            versuche {
                if (z.registrieren) {
                    teamplaner.registrieren(z.server, z.code, z.name, z.email, z.passwort)
                } else {
                    teamplaner.anmelden(z.server, z.email, z.passwort)
                }
            }.onSuccess {
                // Formular leeren, falls sich später jemand ab- und neu anmeldet.
                _zustand.update { it.copy(laeuft = false, passwort = "", code = "") }
            }.onFailure { e -> _zustand.update { it.copy(laeuft = false, fehler = e.meldung()) } }
        }
    }
}
