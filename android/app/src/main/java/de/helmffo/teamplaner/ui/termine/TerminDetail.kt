package de.helmffo.teamplaner.ui.termine

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import de.helmffo.teamplaner.daten.AntwortSetzen
import de.helmffo.teamplaner.daten.Bereich
import de.helmffo.teamplaner.daten.Status
import de.helmffo.teamplaner.daten.TeamKurz
import de.helmffo.teamplaner.daten.Teamplaner
import de.helmffo.teamplaner.daten.Teilnehmer
import de.helmffo.teamplaner.daten.Termin
import de.helmffo.teamplaner.daten.meldung
import de.helmffo.teamplaner.daten.versuche
import de.helmffo.teamplaner.ui.fabrik
import de.helmffo.teamplaner.ui.tagLang
import de.helmffo.teamplaner.ui.uhrzeit
import de.helmffo.teamplaner.ui.zeitpunkt
import de.helmffo.teamplaner.ui.zeitspanne
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

data class DetailZustand(
    val termin: Termin? = null,
    val team: TeamKurz? = null,
    val ichId: Int? = null,
    val meldung: String? = null,
    val geloescht: Boolean = false,
) {
    val leitung: Boolean get() = team?.rolle?.darfEinladen == true
}

class TerminDetailViewModel(
    private val teamplaner: Teamplaner,
    private val teamId: Int,
    private val terminId: Int,
) : ViewModel() {
    private val _zustand = MutableStateFlow(DetailZustand())
    val zustand: StateFlow<DetailZustand> = _zustand

    fun laden() = ausfuehren {
        val api = teamplaner.api()
        val ich = api.ich()
        val termin = api.termin(teamId, terminId)
        _zustand.update { it.copy(termin = termin, team = ich.teams.firstOrNull { t -> t.id == teamId }, ichId = ich.id) }
    }

    private fun ausfuehren(erfolg: String? = null, aktion: suspend () -> Unit) {
        viewModelScope.launch {
            versuche { aktion() }
                .onSuccess { if (erfolg != null) _zustand.update { it.copy(meldung = erfolg) } }
                .onFailure { e -> _zustand.update { it.copy(meldung = e.meldung()) } }
        }
    }

    fun antworten(status: Status, grund: String? = null, fuer: Teilnehmer? = null) = ausfuehren(
        erfolg = fuer?.let { "${it.name}: ${status.anzeige}" },
    ) {
        val neu = teamplaner.api().antworten(teamId, terminId, AntwortSetzen(status, grund, fuer?.nutzerId))
        _zustand.update { it.copy(termin = neu) }
    }

    fun absagen(abgesagt: Boolean, grund: String?, bereich: Bereich) = ausfuehren(
        erfolg = if (abgesagt) "Termin abgesagt" else "Termin findet wieder statt",
    ) {
        val aenderung = buildJsonObject {
            put("abgesagt", JsonPrimitive(abgesagt))
            if (abgesagt) put("absage_grund", JsonPrimitive(grund))
        }
        val neu = teamplaner.api().terminAendern(teamId, terminId, aenderung, bereich)
        _zustand.update { it.copy(termin = neu) }
    }

    fun loeschen(bereich: Bereich) = ausfuehren {
        teamplaner.api().terminLoeschen(teamId, terminId, bereich)
        _zustand.update { it.copy(geloescht = true) }
    }

    fun meldungGezeigt() = _zustand.update { it.copy(meldung = null) }
}

/** Text für WhatsApp & Co.: wer hat noch nicht geantwortet? */
fun offeneText(t: Termin): String {
    val offen = t.teilnehmer.filter { it.status == Status.OFFEN }.joinToString(", ") { it.name }
    val b = zeitpunkt(t.beginn)
    return "${t.anzeigeTitel} am ${tagLang(b)} um ${uhrzeit(b)} Uhr:\n" +
        "Noch keine Antwort von $offen.\nBitte im Teamplaner zu- oder absagen."
}

private enum class Aktion { ABSAGEN, LOESCHEN }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminDetailScreen(
    teamId: Int,
    terminId: Int,
    zurueck: () -> Unit,
    bearbeiten: () -> Unit,
    vm: TerminDetailViewModel = viewModel(factory = fabrik { TerminDetailViewModel(it, teamId, terminId) }),
) {
    val z by vm.zustand.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val kontext = LocalContext.current
    var menue by remember { mutableStateOf(false) }
    var aktion by rememberSaveable { mutableStateOf<Aktion?>(null) }
    var grundFuer by remember { mutableStateOf<Pair<Status, Teilnehmer?>?>(null) }
    var teilnehmerWahl by remember { mutableStateOf<Teilnehmer?>(null) }

    LaunchedEffect(Unit) { vm.laden() }
    LaunchedEffect(z.geloescht) { if (z.geloescht) zurueck() }
    LaunchedEffect(z.meldung) { z.meldung?.let { snackbar.showSnackbar(it); vm.meldungGezeigt() } }

    val t = z.termin
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t?.art?.anzeige ?: "Termin") },
                navigationIcon = {
                    IconButton(onClick = zurueck) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") }
                },
                actions = {
                    if (z.leitung && t != null) {
                        IconButton(onClick = bearbeiten) { Icon(Icons.Default.Edit, "Bearbeiten") }
                        Box {
                            IconButton(onClick = { menue = true }) { Icon(Icons.Default.MoreVert, "Mehr") }
                            DropdownMenu(expanded = menue, onDismissRequest = { menue = false }) {
                                if (t.zaehler.offen > 0) {
                                    DropdownMenuItem(text = { Text("Offene Antworten teilen") }, onClick = {
                                        menue = false
                                        val senden = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, offeneText(t))
                                        }
                                        kontext.startActivity(Intent.createChooser(senden, "Erinnerung teilen"))
                                    })
                                }
                                DropdownMenuItem(
                                    text = { Text(if (t.abgesagt) "Doch stattfinden lassen" else "Absagen") },
                                    onClick = {
                                        menue = false
                                        if (t.abgesagt) vm.absagen(false, null, Bereich.EINZELN) else aktion = Aktion.ABSAGEN
                                    },
                                )
                                DropdownMenuItem(text = { Text("Löschen") }, onClick = { menue = false; aktion = Aktion.LOESCHEN })
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { innen ->
        if (t == null) {
            Box(Modifier.fillMaxSize().padding(innen), Alignment.Center) { CircularProgressIndicator() }
        } else {
            Inhalt(
                t = t,
                leitung = z.leitung,
                ichId = z.ichId,
                modifier = Modifier.padding(innen),
                antworten = { s -> if (s == Status.ABGESAGT || s == Status.VIELLEICHT) grundFuer = s to null else vm.antworten(s) },
                teilnehmerGewaehlt = { if (z.leitung) teilnehmerWahl = it },
            )
        }
    }

    grundFuer?.let { (s, wer) ->
        GrundDialog(s, wer?.name, schliessen = { grundFuer = null }, bestaetigen = { vm.antworten(s, it, wer) })
    }
    teilnehmerWahl?.let { p ->
        StatusFuerDialog(p, schliessen = { teilnehmerWahl = null }) { s ->
            teilnehmerWahl = null
            if (s == Status.ABGESAGT || s == Status.VIELLEICHT) grundFuer = s to p else vm.antworten(s, fuer = p)
        }
    }
    if (t != null) when (aktion) {
        Aktion.ABSAGEN -> AbsagenDialog(t.istSerie, schliessen = { aktion = null }) { grund, bereich -> vm.absagen(true, grund, bereich) }
        Aktion.LOESCHEN -> BereichDialog(
            titel = "Termin löschen?",
            text = "Alle Zu- und Absagen gehen dabei verloren.",
            knopf = "Löschen",
            serie = t.istSerie,
            schliessen = { aktion = null },
            bestaetigen = vm::loeschen,
        )
        null -> Unit
    }
}

@Composable
private fun Inhalt(
    t: Termin,
    leitung: Boolean,
    ichId: Int?,
    modifier: Modifier,
    antworten: (Status) -> Unit,
    teilnehmerGewaehlt: (Teilnehmer) -> Unit,
) {
    val gruppen = t.teilnehmer.groupBy { it.status }
    LazyColumn(modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(t.anzeigeTitel, style = MaterialTheme.typography.headlineSmall)
                when (t.heimspiel) {
                    true -> Text("Heimspiel")
                    false -> Text("Auswärtsspiel")
                    null -> Unit
                }
                Text(tagLang(zeitpunkt(t.beginn)), style = MaterialTheme.typography.titleMedium)
                Text(zeitspanne(t))
                t.ort?.let { Text(it) }
                t.notiz?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                t.fristStunden?.takeIf { it > 0 }?.let {
                    val bis = zeitpunkt(t.antwortBis)
                    Text("Antworten bis ${tagLang(bis)}, ${uhrzeit(bis)} Uhr", style = MaterialTheme.typography.bodySmall)
                }
                if (t.abgesagt) {
                    Text(
                        "Abgesagt" + (t.absageGrund?.let { ": $it" } ?: ""),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
        if (!t.abgesagt) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Deine Antwort", style = MaterialTheme.typography.labelLarge)
                        Text(
                            t.eigene.status.anzeige + (if (t.eigene.automatisch && t.eigene.status != Status.OFFEN) " (automatisch)" else "") +
                                (t.eigene.grund?.let { " – $it" } ?: ""),
                            color = t.eigene.status.farbe(),
                        )
                    }
                    AntwortKnoepfe(t.eigene.status, t.antwortMoeglich(leitung), antworten)
                }
                HorizontalDivider(Modifier.padding(top = 12.dp))
            }
        }
        Status.entries.forEach { s ->
            val liste = gruppen[s].orEmpty()
            if (liste.isNotEmpty()) {
                item(key = "kopf-$s") {
                    Row(Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(s.symbol(), null, tint = s.farbe(), modifier = Modifier.size(18.dp))
                        Text(
                            " ${s.anzeige} (${liste.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = s.farbe(),
                        )
                    }
                }
                items(liste, key = { "p-${it.nutzerId}" }) { p ->
                    ListItem(
                        headlineContent = { Text(if (p.nutzerId == ichId) "${p.name} (du)" else p.name) },
                        supportingContent = {
                            val infos = listOfNotNull(
                                p.rolle.takeIf { it != de.helmffo.teamplaner.daten.Rolle.SPIELER }?.anzeige,
                                p.grund,
                                "automatisch".takeIf { p.automatisch && p.status != Status.OFFEN },
                            )
                            if (infos.isNotEmpty()) Text(infos.joinToString(" · "))
                        },
                        modifier = if (leitung) Modifier.clickable { teilnehmerGewaehlt(p) } else Modifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusFuerDialog(p: Teilnehmer, schliessen: () -> Unit, waehlen: (Status) -> Unit) {
    AlertDialog(
        onDismissRequest = schliessen,
        title = { Text("Antwort für ${p.name}") },
        text = {
            Column {
                Text(
                    "Zum Beispiel, wenn jemand persönlich Bescheid gegeben hat.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Status.entries.forEach { s ->
                    ListItem(
                        leadingContent = { Icon(s.symbol(), null, tint = s.farbe()) },
                        headlineContent = { Text(if (s == Status.OFFEN) "Antwort entfernen" else s.anzeige) },
                        modifier = Modifier.clickable { waehlen(s) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = schliessen) { Text("Abbrechen") } },
    )
}

@Composable
private fun AbsagenDialog(serie: Boolean, schliessen: () -> Unit, bestaetigen: (String?, Bereich) -> Unit) {
    var grund by rememberSaveable { mutableStateOf("") }
    var folgende by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = schliessen,
        title = { Text("Termin absagen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Der Termin bleibt sichtbar und ist als abgesagt markiert.")
                OutlinedTextField(
                    value = grund,
                    onValueChange = { grund = it.take(200) },
                    label = { Text("Grund, z. B. „Platz gesperrt“") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (serie) BereichAuswahl(folgende) { folgende = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                bestaetigen(grund.trim().ifEmpty { null }, if (folgende) Bereich.FOLGENDE else Bereich.EINZELN)
                schliessen()
            }) { Text("Absagen") }
        },
        dismissButton = { TextButton(onClick = schliessen) { Text("Abbrechen") } },
    )
}

/** Bestätigung mit Auswahl „nur dieser / dieser und folgende“ bei Serien. */
@Composable
fun BereichDialog(
    titel: String,
    text: String,
    knopf: String,
    serie: Boolean,
    schliessen: () -> Unit,
    bestaetigen: (Bereich) -> Unit,
) {
    var folgende by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = schliessen,
        title = { Text(titel) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text)
                if (serie) BereichAuswahl(folgende) { folgende = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { bestaetigen(if (folgende) Bereich.FOLGENDE else Bereich.EINZELN); schliessen() }) { Text(knopf) }
        },
        dismissButton = { TextButton(onClick = schliessen) { Text("Abbrechen") } },
    )
}

@Composable
private fun BereichAuswahl(folgende: Boolean, aendern: (Boolean) -> Unit) {
    Column {
        listOf(false to "Nur diesen Termin", true to "Diesen und alle folgenden der Serie").forEach { (wert, text) ->
            Row(
                Modifier.fillMaxWidth().clickable { aendern(wert) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.RadioButton(selected = folgende == wert, onClick = { aendern(wert) })
                Text(text)
            }
        }
    }
}
