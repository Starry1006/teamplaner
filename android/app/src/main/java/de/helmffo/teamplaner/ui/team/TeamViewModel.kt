package de.helmffo.teamplaner.ui.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.helmffo.teamplaner.daten.CodeEinloesen
import de.helmffo.teamplaner.daten.Einladung
import de.helmffo.teamplaner.daten.EinladungNeu
import de.helmffo.teamplaner.daten.Ich
import de.helmffo.teamplaner.daten.Mitglied
import de.helmffo.teamplaner.daten.Rolle
import de.helmffo.teamplaner.daten.RolleAendern
import de.helmffo.teamplaner.daten.TeamKurz
import de.helmffo.teamplaner.daten.TeamNeu
import de.helmffo.teamplaner.daten.Teamplaner
import de.helmffo.teamplaner.daten.TeamplanerApi
import de.helmffo.teamplaner.daten.meldung
import de.helmffo.teamplaner.daten.versuche
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TeamZustand(
    val laedt: Boolean = true,
    val aktualisiert: Boolean = false,
    val server: String = "",
    val ich: Ich? = null,
    val team: TeamKurz? = null,
    val mitglieder: List<Mitglied> = emptyList(),
    val einladungen: List<Einladung> = emptyList(),
    /** Kurzmeldung (Fehler oder Bestätigung) für die Snackbar. */
    val meldung: String? = null,
    /** Gerade erstellte Einladung – wird zum Teilen angezeigt. */
    val neueEinladung: Einladung? = null,
) {
    val rolle: Rolle? get() = team?.rolle
}

class TeamViewModel(private val teamplaner: Teamplaner) : ViewModel() {
    private val _zustand = MutableStateFlow(TeamZustand())
    val zustand: StateFlow<TeamZustand> = _zustand

    fun laden(pullToRefresh: Boolean = false) {
        if (pullToRefresh) _zustand.update { it.copy(aktualisiert = true) }
        viewModelScope.launch {
            versuche { neuLaden() }.onFailure { e ->
                _zustand.update { it.copy(meldung = e.meldung()) }
            }
            _zustand.update { it.copy(laedt = false, aktualisiert = false) }
        }
    }

    private suspend fun neuLaden(gewuenschtesTeam: Int? = null) {
        val api = teamplaner.api()
        val server = teamplaner.sitzung.first()?.server.orEmpty()
        val ich = api.ich()
        val wunsch = gewuenschtesTeam ?: teamplaner.aktivesTeam.first()
        val team = ich.teams.firstOrNull { it.id == wunsch } ?: ich.teams.firstOrNull()
        if (team != null && team.id != wunsch) teamplaner.teamWaehlen(team.id)
        val mitglieder = team?.let { api.mitglieder(it.id) }.orEmpty()
        val einladungen = if (team?.rolle?.darfEinladen == true) api.einladungen(team.id) else emptyList()
        _zustand.update {
            it.copy(server = server, ich = ich, team = team, mitglieder = mitglieder, einladungen = einladungen)
        }
    }

    /** Führt eine Änderung aus, lädt danach neu und meldet Erfolg oder Fehler. */
    private fun aendern(
        erfolg: String? = null,
        teamDanach: (() -> Int?)? = null,
        aktion: suspend TeamplanerApi.() -> Unit,
    ) {
        viewModelScope.launch {
            versuche {
                teamplaner.api().aktion()
                neuLaden(teamDanach?.invoke())
            }.onSuccess {
                if (erfolg != null) _zustand.update { it.copy(meldung = erfolg) }
            }.onFailure { e ->
                _zustand.update { it.copy(meldung = e.meldung()) }
            }
        }
    }

    fun teamWaehlen(teamId: Int) {
        _zustand.update { it.copy(laedt = true) }
        viewModelScope.launch {
            teamplaner.teamWaehlen(teamId)
            versuche { neuLaden(teamId) }.onFailure { e -> _zustand.update { it.copy(meldung = e.meldung()) } }
            _zustand.update { it.copy(laedt = false) }
        }
    }

    fun einladen(rolle: Rolle, einmalig: Boolean) {
        val team = _zustand.value.team ?: return
        aendern {
            val einladung = einladen(team.id, EinladungNeu(rolle = rolle, maxNutzungen = if (einmalig) 1 else null))
            _zustand.update { it.copy(neueEinladung = einladung) }
        }
    }

    fun einladungLoeschen(einladung: Einladung) {
        val team = _zustand.value.team ?: return
        aendern("Einladung gelöscht") { einladungLoeschen(team.id, einladung.id) }
    }

    fun rolleAendern(mitglied: Mitglied, rolle: Rolle) {
        val team = _zustand.value.team ?: return
        aendern("${mitglied.name} ist jetzt ${rolle.anzeige}") { rolleAendern(team.id, mitglied.nutzerId, RolleAendern(rolle)) }
    }

    fun entfernen(mitglied: Mitglied) {
        val team = _zustand.value.team ?: return
        aendern("${mitglied.name} entfernt") { mitgliedEntfernen(team.id, mitglied.nutzerId) }
    }

    fun austreten() {
        val z = _zustand.value
        val team = z.team ?: return
        val ich = z.ich ?: return
        aendern("Du hast „${team.name}“ verlassen") { mitgliedEntfernen(team.id, ich.id) }
    }

    fun codeEinloesen(code: String) {
        var neu: Int? = null
        aendern("Team beigetreten", teamDanach = { neu }) { neu = codeEinloesen(CodeEinloesen(code.trim())).id }
    }

    fun teamAnlegen(name: String) {
        var neu: Int? = null
        aendern("Team angelegt", teamDanach = { neu }) { neu = teamAnlegen(TeamNeu(name.trim())).id }
    }

    fun meldungGezeigt() = _zustand.update { it.copy(meldung = null) }

    fun einladungGezeigt() = _zustand.update { it.copy(neueEinladung = null) }
}
