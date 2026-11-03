package de.helmffo.teamplaner.ui.termine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.helmffo.teamplaner.daten.Standardantworten
import de.helmffo.teamplaner.daten.Status
import de.helmffo.teamplaner.daten.Termin
import de.helmffo.teamplaner.daten.Terminart
import de.helmffo.teamplaner.ui.fabrik
import de.helmffo.teamplaner.ui.tagUeberschrift
import de.helmffo.teamplaner.ui.zeitpunkt
import de.helmffo.teamplaner.ui.zeitspanne

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermineScreen(
    oeffnen: (teamId: Int, terminId: Int) -> Unit,
    neu: (teamId: Int) -> Unit,
    statistik: (teamId: Int) -> Unit,
    profilSymbol: @Composable () -> Unit,
    vm: TermineViewModel = viewModel(factory = fabrik { TermineViewModel(it) }),
) {
    val z by vm.zustand.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var grundFuer by remember { mutableStateOf<Pair<Termin, Status>?>(null) }
    var menue by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.laden() }
    LaunchedEffect(z.meldung) {
        z.meldung?.let { snackbar.showSnackbar(it); vm.meldungGezeigt() }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Termine")
                        z.team?.let {
                            Text(it.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                },
                actions = {
                    profilSymbol()
                    if (z.leitung) {
                        Box {
                            IconButton(onClick = { menue = true }) { Icon(Icons.Default.MoreVert, "Mehr") }
                            DropdownMenu(expanded = menue, onDismissRequest = { menue = false }) {
                                DropdownMenuItem(
                                    text = { Text("Trainingsbeteiligung") },
                                    onClick = { menue = false; z.team?.let { statistik(it.id) } },
                                )
                                if (z.team?.rolle?.istAdmin == true) {
                                    DropdownMenuItem(
                                        text = { Text("Standardantworten") },
                                        onClick = { menue = false; vm.standardLaden() },
                                    )
                                }
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (z.leitung) {
                ExtendedFloatingActionButton(
                    onClick = { z.team?.let { neu(it.id) } },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("Termin") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { innen ->
        Column(Modifier.fillMaxSize().padding(innen)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                SegmentedButton(
                    selected = !z.vergangene,
                    onClick = { vm.vergangeneZeigen(false) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text("Kommende") }
                SegmentedButton(
                    selected = z.vergangene,
                    onClick = { vm.vergangeneZeigen(true) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text("Vergangene") }
            }
            PullToRefreshBox(
                isRefreshing = z.aktualisiert,
                onRefresh = { vm.laden(pullToRefresh = true) },
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    z.laedt && z.termine.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                    z.keinTeam -> Hinweis("Du bist noch in keinem Team. Unter „Team“ kannst du einen Code einlösen.")
                    z.termine.isEmpty() -> Hinweis(
                        when {
                            z.vergangene -> "Noch keine vergangenen Termine."
                            z.leitung -> "Noch keine Termine. Lege mit „Termin“ das Training als Serie an."
                            else -> "Noch keine Termine geplant."
                        },
                    )
                    else -> Terminliste(
                        termine = z.termine,
                        leitung = z.leitung,
                        oeffnen = { t -> z.team?.let { oeffnen(it.id, t.id) } },
                        antworten = { t, s ->
                            if (s == Status.ABGESAGT || s == Status.VIELLEICHT) grundFuer = t to s else vm.antworten(t, s)
                        },
                    )
                }
            }
        }
    }

    grundFuer?.let { (t, s) ->
        GrundDialog(s, schliessen = { grundFuer = null }, bestaetigen = { vm.antworten(t, s, it) })
    }
    z.standard?.let { s ->
        StandardDialog(s, schliessen = vm::standardSchliessen, speichern = vm::standardSpeichern)
    }
}

@Composable
private fun Terminliste(
    termine: List<Termin>,
    leitung: Boolean,
    oeffnen: (Termin) -> Unit,
    antworten: (Termin, Status) -> Unit,
) {
    val nachTag = termine.groupBy { zeitpunkt(it.beginn).toLocalDate() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 88.dp)) {
        nachTag.forEach { (tag, liste) ->
            item(key = "tag-$tag") {
                Text(
                    tagUeberschrift(tag),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                )
            }
            items(liste, key = { it.id }) { t -> TerminKarte(t, leitung, { oeffnen(t) }, { s -> antworten(t, s) }) }
        }
    }
}

@Composable
private fun TerminKarte(t: Termin, leitung: Boolean, oeffnen: () -> Unit, antworten: (Status) -> Unit) {
    Card(
        onClick = oeffnen,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (t.art == Terminart.SPIEL) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(t.art.symbol(), contentDescription = t.art.anzeige, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    t.anzeigeTitel + when (t.heimspiel) { true -> " (H)"; false -> " (A)"; null -> "" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (t.abgesagt) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(zeitspanne(t), style = MaterialTheme.typography.bodyMedium)
                t.ort?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                if (t.abgesagt) {
                    Text(
                        "Abgesagt" + (t.absageGrund?.let { ": $it" } ?: ""),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelLarge,
                    )
                } else {
                    Zaehlerzeile(t.zaehler)
                }
            }
            if (!t.abgesagt) {
                AntwortKnoepfe(
                    aktuell = if (t.eigene.automatisch && t.eigene.status == Status.OFFEN) Status.OFFEN else t.eigene.status,
                    aktiv = t.antwortMoeglich(leitung),
                    waehlen = antworten,
                )
            }
        }
    }
}

@Composable
private fun Hinweis(text: String) {
    // Scrollbar, damit Pull-to-Refresh auch ohne Inhalte funktioniert.
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Spacer(Modifier.height(64.dp))
            Text(
                text,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(32.dp),
            )
        }
    }
}

@Composable
private fun StandardDialog(start: Standardantworten, schliessen: () -> Unit, speichern: (Standardantworten) -> Unit) {
    var s by remember { mutableStateOf(start) }
    AlertDialog(
        onDismissRequest = schliessen,
        title = { Text("Standardantworten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Was gilt, solange jemand nicht antwortet? Bei „Zugesagt“ müssen sich Spieler nur abmelden.",
                    style = MaterialTheme.typography.bodySmall,
                )
                listOf(
                    Terminart.TRAINING to s.training,
                    Terminart.SPIEL to s.spiel,
                    Terminart.EVENT to s.event,
                ).forEach { (art, wert) ->
                    Text(art.anzeige, style = MaterialTheme.typography.labelLarge)
                    Row {
                        listOf(Status.OFFEN to "Keine Antwort", Status.ZUGESAGT to "Zugesagt").forEach { (st, text) ->
                            Row(
                                Modifier
                                    .weight(1f)
                                    .selectable(selected = wert == st, role = Role.RadioButton, onClick = {
                                        s = when (art) {
                                            Terminart.TRAINING -> s.copy(training = st)
                                            Terminart.SPIEL -> s.copy(spiel = st)
                                            Terminart.EVENT -> s.copy(event = st)
                                        }
                                    }),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = wert == st, onClick = null)
                                Text(text)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { speichern(s) }) { Text("Speichern") } },
        dismissButton = { TextButton(onClick = schliessen) { Text("Abbrechen") } },
    )
}
