package de.helmffo.teamplaner.ui.termine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import de.helmffo.teamplaner.daten.Bereich
import de.helmffo.teamplaner.daten.Teamplaner
import de.helmffo.teamplaner.daten.Termin
import de.helmffo.teamplaner.daten.TerminNeu
import de.helmffo.teamplaner.daten.Terminart
import de.helmffo.teamplaner.daten.Wiederholung
import de.helmffo.teamplaner.daten.meldung
import de.helmffo.teamplaner.daten.versuche
import de.helmffo.teamplaner.ui.WOCHENTAGE
import de.helmffo.teamplaner.ui.alsIso
import de.helmffo.teamplaner.ui.fabrik
import de.helmffo.teamplaner.ui.zeitpunkt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Eingaben des Formulars – Zeiten in lokaler Zeit. */
data class Formular(
    val art: Terminart = Terminart.TRAINING,
    val titel: String = "",
    val gegner: String = "",
    val heimspiel: Boolean? = true,
    val datum: LocalDate = LocalDate.now().plusDays(1),
    val beginn: LocalTime = LocalTime.of(19, 0),
    val ende: LocalTime? = LocalTime.of(20, 30),
    val treffenVorher: Int? = null,
    val ort: String = "",
    val notiz: String = "",
    val frist: Int? = null,
    val wiederholen: Boolean = false,
    val wochentage: Set<Int> = emptySet(),
    val bis: LocalDate = LocalDate.now().plusWeeks(12),
) {
    fun beginnZeit(zone: ZoneId = ZoneId.systemDefault()): ZonedDateTime = ZonedDateTime.of(datum, beginn, zone)

    /** Ende vor Beginn heißt: am nächsten Tag (z. B. Mannschaftsabend bis 01:00). */
    fun endeZeit(zone: ZoneId = ZoneId.systemDefault()): ZonedDateTime? = ende?.let {
        val tag = if (it <= beginn) datum.plusDays(1) else datum
        ZonedDateTime.of(tag, it, zone)
    }

    private fun text(s: String) = s.trim().ifEmpty { null }

    val gegnerWert: String? get() = if (art == Terminart.SPIEL) text(gegner) else null
    val heimWert: Boolean? get() = if (art == Terminart.SPIEL) heimspiel else null

    fun neu(): TerminNeu = TerminNeu(
        art = art,
        beginn = alsIso(beginnZeit()),
        ende = endeZeit()?.let(::alsIso),
        titel = text(titel),
        treffenVorherMin = treffenVorher,
        ort = text(ort),
        notiz = text(notiz),
        gegner = gegnerWert,
        heimspiel = heimWert,
        fristStunden = frist,
        wiederholung = if (wiederholen) {
            Wiederholung(wochentage.sorted().ifEmpty { listOf(datum.dayOfWeek.value - 1) }, bis.toString())
        } else {
            null
        },
    )

    /** Nur geänderte Felder – sonst würden Einzeländerungen in Serien überschrieben. */
    fun aenderungen(alt: Termin): JsonObject {
        val felder = mutableMapOf<String, JsonElement>()
        fun pruefe(name: String, neu: Any?, vorher: Any?) {
            if (neu != vorher) felder[name] = when (neu) {
                null -> JsonNull
                is String -> JsonPrimitive(neu)
                is Number -> JsonPrimitive(neu)
                is Boolean -> JsonPrimitive(neu)
                else -> JsonPrimitive(neu.toString())
            }
        }
        val neuerBeginn = beginnZeit()
        if (neuerBeginn.toInstant() != zeitpunkt(alt.beginn).toInstant()) felder["beginn"] = JsonPrimitive(alsIso(neuerBeginn))
        val neuesEnde = endeZeit()
        if (neuesEnde?.toInstant() != alt.ende?.let { zeitpunkt(it).toInstant() }) {
            felder["ende"] = neuesEnde?.let { JsonPrimitive(alsIso(it)) } ?: JsonNull
        }
        if (art != alt.art) felder["art"] = JsonPrimitive(art.name.lowercase())
        pruefe("titel", text(titel), alt.titel)
        pruefe("gegner", gegnerWert, alt.gegner)
        pruefe("heimspiel", heimWert, alt.heimspiel)
        pruefe("treffen_vorher_min", treffenVorher, alt.treffenVorherMin)
        pruefe("ort", text(ort), alt.ort)
        pruefe("notiz", text(notiz), alt.notiz)
        pruefe("frist_stunden", frist, alt.fristStunden)
        return JsonObject(felder)
    }

    companion object {
        fun aus(t: Termin): Formular {
            val b = zeitpunkt(t.beginn)
            return Formular(
                art = t.art,
                titel = t.titel.orEmpty(),
                gegner = t.gegner.orEmpty(),
                heimspiel = t.heimspiel,
                datum = b.toLocalDate(),
                beginn = b.toLocalTime(),
                ende = t.ende?.let { zeitpunkt(it).toLocalTime() },
                treffenVorher = t.treffenVorherMin,
                ort = t.ort.orEmpty(),
                notiz = t.notiz.orEmpty(),
                frist = t.fristStunden,
            )
        }
    }
}

data class BearbeitenZustand(
    val formular: Formular = Formular(),
    val original: Termin? = null,
    val laedt: Boolean = false,
    val speichert: Boolean = false,
    val fertig: Boolean = false,
    val meldung: String? = null,
)

class TerminBearbeitenViewModel(
    private val teamplaner: Teamplaner,
    private val teamId: Int,
    private val terminId: Int?,
) : ViewModel() {
    private val _zustand = MutableStateFlow(BearbeitenZustand(laedt = terminId != null))
    val zustand: StateFlow<BearbeitenZustand> = _zustand

    init {
        if (terminId != null) {
            viewModelScope.launch {
                versuche { teamplaner.api().termin(teamId, terminId) }
                    .onSuccess { t -> _zustand.update { it.copy(formular = Formular.aus(t), original = t, laedt = false) } }
                    .onFailure { e -> _zustand.update { it.copy(meldung = e.meldung(), laedt = false) } }
            }
        }
    }

    fun aendern(f: (Formular) -> Formular) = _zustand.update { it.copy(formular = f(it.formular)) }

    fun speichern(bereich: Bereich = Bereich.EINZELN) {
        val z = _zustand.value
        if (z.speichert) return
        val f = z.formular
        val problem = when {
            f.wiederholen && f.bis < f.datum -> "Das Serienende liegt vor dem ersten Termin."
            f.ende != null && f.ende == f.beginn -> "Beginn und Ende sind gleich."
            else -> null
        }
        if (problem != null) {
            _zustand.update { it.copy(meldung = problem) }
            return
        }
        _zustand.update { it.copy(speichert = true) }
        viewModelScope.launch {
            versuche {
                val api = teamplaner.api()
                val original = z.original
                if (original == null) {
                    val neu = api.terminAnlegen(teamId, f.neu())
                    if (neu.size > 1) "${neu.size} Termine angelegt" else "Termin angelegt"
                } else {
                    val aenderungen = f.aenderungen(original)
                    if (aenderungen.isNotEmpty()) api.terminAendern(teamId, original.id, aenderungen, bereich)
                    "Gespeichert"
                }
            }.onSuccess { text -> _zustand.update { it.copy(speichert = false, fertig = true, meldung = text) } }
                .onFailure { e -> _zustand.update { it.copy(speichert = false, meldung = e.meldung()) } }
        }
    }

    fun meldungGezeigt() = _zustand.update { it.copy(meldung = null) }
}

private val DATUM = DateTimeFormatter.ofPattern("EEE, dd.MM.yyyy", Locale.GERMANY)
private val UHR = DateTimeFormatter.ofPattern("HH:mm")

private val TREFFEN = listOf(null to "Kein Treffpunkt", 15 to "15 Min. vorher", 30 to "30 Min. vorher", 45 to "45 Min. vorher", 60 to "1 Std. vorher", 90 to "1,5 Std. vorher", 120 to "2 Std. vorher")
private val FRIST = listOf(null to "Bis Beginn", 2 to "2 Std. vorher", 12 to "12 Std. vorher", 24 to "1 Tag vorher", 48 to "2 Tage vorher", 72 to "3 Tage vorher")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TerminBearbeitenScreen(
    teamId: Int,
    terminId: Int?,
    fertig: () -> Unit,
    vm: TerminBearbeitenViewModel = viewModel(factory = fabrik { TerminBearbeitenViewModel(it, teamId, terminId) }),
) {
    val z by vm.zustand.collectAsStateWithLifecycle()
    val f = z.formular
    val snackbar = remember { SnackbarHostState() }
    var bereichFragen by remember { mutableStateOf(false) }

    LaunchedEffect(z.fertig) { if (z.fertig) fertig() }
    LaunchedEffect(z.meldung) { z.meldung?.let { if (!z.fertig) snackbar.showSnackbar(it); vm.meldungGezeigt() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (terminId == null) "Neuer Termin" else "Termin bearbeiten") },
                navigationIcon = { IconButton(onClick = fertig) { Icon(Icons.Default.Close, "Abbrechen") } },
                actions = {
                    TextButton(
                        enabled = !z.speichert && !z.laedt,
                        onClick = { if (z.original?.istSerie == true) bereichFragen = true else vm.speichern() },
                    ) { Text("Speichern") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { innen ->
        if (z.laedt) {
            Box(Modifier.fillMaxSize().padding(innen), Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val feld = Modifier.fillMaxWidth()
        Column(
            Modifier.fillMaxSize().padding(innen).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SingleChoiceSegmentedButtonRow(feld) {
                Terminart.entries.forEachIndexed { i, art ->
                    SegmentedButton(
                        selected = f.art == art,
                        onClick = { vm.aendern { it.copy(art = art) } },
                        shape = SegmentedButtonDefaults.itemShape(i, Terminart.entries.size),
                        icon = { Icon(art.symbol(), null) },
                    ) { Text(art.anzeige) }
                }
            }
            if (f.art == Terminart.SPIEL) {
                OutlinedTextField(
                    value = f.gegner,
                    onValueChange = { v -> vm.aendern { it.copy(gegner = v.take(100)) } },
                    label = { Text("Gegner") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = feld,
                )
                SingleChoiceSegmentedButtonRow(feld) {
                    listOf(true to "Heim", false to "Auswärts").forEachIndexed { i, (wert, text) ->
                        SegmentedButton(
                            selected = f.heimspiel == wert,
                            onClick = { vm.aendern { it.copy(heimspiel = if (it.heimspiel == wert) null else wert) } },
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                        ) { Text(text) }
                    }
                }
            }
            OutlinedTextField(
                value = f.titel,
                onValueChange = { v -> vm.aendern { it.copy(titel = v.take(100)) } },
                label = { Text("Titel (optional)") },
                placeholder = { Text(if (f.art == Terminart.SPIEL) "z. B. Pokal 2. Runde" else "z. B. Torwarttraining") },
                singleLine = true,
                modifier = feld,
            )

            DatumFeld("Datum", f.datum, feld) { d -> vm.aendern { it.copy(datum = d) } }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ZeitFeld("Beginn", f.beginn, Modifier.weight(1f)) { t ->
                    if (t != null) vm.aendern {
                        // Ende mitverschieben, damit die Dauer gleich bleibt.
                        val dauer = it.ende?.let { e -> java.time.Duration.between(it.beginn, e) }
                        it.copy(beginn = t, ende = dauer?.let { d -> t.plus(d) } ?: it.ende)
                    }
                }
                ZeitFeld("Ende", f.ende, Modifier.weight(1f), entfernbar = true) { t -> vm.aendern { it.copy(ende = t) } }
            }
            AuswahlFeld("Treffpunkt", TREFFEN, f.treffenVorher, feld) { w -> vm.aendern { it.copy(treffenVorher = w) } }
            OutlinedTextField(
                value = f.ort,
                onValueChange = { v -> vm.aendern { it.copy(ort = v.take(200)) } },
                label = { Text("Ort") },
                placeholder = { Text("z. B. Sportplatz, Kunstrasen") },
                singleLine = true,
                modifier = feld,
            )
            AuswahlFeld("Antworten möglich", FRIST, f.frist, feld) { w -> vm.aendern { it.copy(frist = w) } }
            OutlinedTextField(
                value = f.notiz,
                onValueChange = { v -> vm.aendern { it.copy(notiz = v.take(2000)) } },
                label = { Text("Notiz") },
                placeholder = { Text("z. B. Trikots mitbringen") },
                minLines = 2,
                modifier = feld,
            )

            if (z.original == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Wöchentlich wiederholen", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    Switch(checked = f.wiederholen, onCheckedChange = { an ->
                        vm.aendern { it.copy(wiederholen = an, wochentage = it.wochentage.ifEmpty { setOf(it.datum.dayOfWeek.value - 1) }) }
                    })
                }
                if (f.wiederholen) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        WOCHENTAGE.forEachIndexed { i, tag ->
                            FilterChip(
                                selected = i in f.wochentage,
                                onClick = { vm.aendern { it.copy(wochentage = if (i in it.wochentage) it.wochentage - i else it.wochentage + i) } },
                                label = { Text(tag) },
                            )
                        }
                    }
                    DatumFeld("Bis einschließlich", f.bis, feld) { d -> vm.aendern { it.copy(bis = d) } }
                    Text(
                        "Tipp: Training bis Saisonende anlegen. Einzelne Termine (z. B. Ferien) danach absagen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (bereichFragen) {
        BereichDialog(
            titel = "Serientermin ändern",
            text = "Welche Termine sollen geändert werden? Eine neue Uhrzeit wird auf alle übertragen.",
            knopf = "Speichern",
            serie = true,
            schliessen = { bereichFragen = false },
            bestaetigen = vm::speichern,
        )
    }
}

/** Nur-Lese-Feld, das beim Antippen einen Dialog öffnet. */
@Composable
private fun TippFeld(label: String, text: String, modifier: Modifier, trailing: @Composable (() -> Unit)? = null, tippen: () -> Unit) {
    Box(modifier) {
        OutlinedTextField(value = text, onValueChange = {}, readOnly = true, label = { Text(label) }, trailingIcon = trailing, modifier = Modifier.fillMaxWidth())
        Box(Modifier.matchParentSize().padding(end = if (trailing != null) 48.dp else 0.dp).clickable(onClick = tippen))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatumFeld(label: String, datum: LocalDate, modifier: Modifier, waehlen: (LocalDate) -> Unit) {
    var offen by remember { mutableStateOf(false) }
    TippFeld(label, datum.format(DATUM), modifier) { offen = true }
    if (offen) {
        val state = rememberDatePickerState(initialSelectedDateMillis = datum.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { offen = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { waehlen(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    offen = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { offen = false }) { Text("Abbrechen") } },
        ) { DatePicker(state) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ZeitFeld(label: String, zeit: LocalTime?, modifier: Modifier, entfernbar: Boolean = false, waehlen: (LocalTime?) -> Unit) {
    var offen by remember { mutableStateOf(false) }
    TippFeld(
        label, zeit?.format(UHR) ?: "–", modifier,
        trailing = if (entfernbar && zeit != null) {
            { IconButton(onClick = { waehlen(null) }) { Icon(Icons.Default.Close, "Kein Ende") } }
        } else null,
    ) { offen = true }
    if (offen) {
        val start = zeit ?: LocalTime.of(20, 30)
        val state = rememberTimePickerState(start.hour, start.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { offen = false },
            text = { TimePicker(state) },
            confirmButton = { TextButton(onClick = { waehlen(LocalTime.of(state.hour, state.minute)); offen = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { offen = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun AuswahlFeld(label: String, optionen: List<Pair<Int?, String>>, wert: Int?, modifier: Modifier, waehlen: (Int?) -> Unit) {
    var offen by remember { mutableStateOf(false) }
    val text = optionen.firstOrNull { it.first == wert }?.second ?: "$wert"
    Box(modifier) {
        TippFeld(label, text, Modifier.fillMaxWidth()) { offen = true }
        DropdownMenu(expanded = offen, onDismissRequest = { offen = false }) {
            optionen.forEach { (w, t) -> DropdownMenuItem(text = { Text(t) }, onClick = { waehlen(w); offen = false }) }
        }
    }
}
