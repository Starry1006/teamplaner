package de.helmffo.teamplaner.ui.termine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.helmffo.teamplaner.daten.AntwortSetzen
import de.helmffo.teamplaner.daten.Standardantworten
import de.helmffo.teamplaner.daten.Status
import de.helmffo.teamplaner.daten.TeamKurz
import de.helmffo.teamplaner.daten.Teamplaner
import de.helmffo.teamplaner.daten.Termin
import de.helmffo.teamplaner.daten.meldung
import de.helmffo.teamplaner.daten.versuche
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.OffsetDateTime

data class TermineZustand(
    val laedt: Boolean = true,
    val aktualisiert: Boolean = false,
    val team: TeamKurz? = null,
    val keinTeam: Boolean = false,
    val vergangene: Boolean = false,
    val termine: List<Termin> = emptyList(),
    val standard: Standardantworten? = null,
    val meldung: String? = null,
) {
    val leitung: Boolean get() = team?.rolle?.darfEinladen == true
}

/** Darf die angemeldete Person (noch) antworten? Trainer immer, Spieler bis zur Frist. */
fun Termin.antwortMoeglich(leitung: Boolean, jetzt: Instant = Instant.now()): Boolean =
    leitung || (!abgesagt && OffsetDateTime.parse(antwortBis).toInstant() > jetzt)

class TermineViewModel(private val teamplaner: Teamplaner) : ViewModel() {
    private val _zustand = MutableStateFlow(TermineZustand())
    val zustand: StateFlow<TermineZustand> = _zustand
    private var ladeJob: Job? = null

    fun laden(pullToRefresh: Boolean = false) {
        if (pullToRefresh) _zustand.update { it.copy(aktualisiert = true) }
        ladeJob?.cancel()
        ladeJob = viewModelScope.launch {
            versuche {
                val api = teamplaner.api()
                val team = teamplaner.aktivesTeam(api.ich())
                val termine = team?.let { api.termine(it.id, _zustand.value.vergangene) }.orEmpty()
                _zustand.update { it.copy(team = team, keinTeam = team == null, termine = termine) }
            }.onFailure { e -> _zustand.update { it.copy(meldung = e.meldung()) } }
            _zustand.update { it.copy(laedt = false, aktualisiert = false) }
        }
    }

    fun vergangeneZeigen(vergangene: Boolean) {
        if (vergangene == _zustand.value.vergangene) return
        _zustand.update { it.copy(vergangene = vergangene, termine = emptyList(), laedt = true) }
        laden()
    }

    fun antworten(termin: Termin, status: Status, grund: String? = null) {
        val team = _zustand.value.team ?: return
        viewModelScope.launch {
            versuche { teamplaner.api().antworten(team.id, termin.id, AntwortSetzen(status, grund)) }
                .onSuccess { neu ->
                    _zustand.update { z ->
                        z.copy(termine = z.termine.map { if (it.id == neu.id) neu.copy(teilnehmer = emptyList()) else it })
                    }
                }
                .onFailure { e -> _zustand.update { it.copy(meldung = e.meldung()) } }
        }
    }

    fun standardLaden() {
        val team = _zustand.value.team ?: return
        viewModelScope.launch {
            versuche { teamplaner.api().standardantworten(team.id) }
                .onSuccess { s -> _zustand.update { it.copy(standard = s) } }
                .onFailure { e -> _zustand.update { it.copy(meldung = e.meldung()) } }
        }
    }

    fun standardSpeichern(s: Standardantworten) {
        val team = _zustand.value.team ?: return
        viewModelScope.launch {
            versuche { teamplaner.api().standardantwortenSetzen(team.id, s) }
                .onSuccess {
                    _zustand.update { it.copy(standard = null, meldung = "Standardantworten gespeichert") }
                    laden()
                }
                .onFailure { e -> _zustand.update { it.copy(meldung = e.meldung()) } }
        }
    }

    fun standardSchliessen() = _zustand.update { it.copy(standard = null) }

    fun meldungGezeigt() = _zustand.update { it.copy(meldung = null) }
}
