package de.helmffo.teamplaner.ui.team

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.helmffo.teamplaner.daten.Einladung
import de.helmffo.teamplaner.daten.Mitglied
import de.helmffo.teamplaner.daten.Rolle
import de.helmffo.teamplaner.ui.datum
import de.helmffo.teamplaner.ui.fabrik

private enum class Dialog { EINLADEN, CODE, TEAM_NEU, VERLASSEN }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamScreen(
    zumProfil: () -> Unit,
    vm: TeamViewModel = viewModel(factory = fabrik { TeamViewModel(it) }),
) {
    val z by vm.zustand.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var dialog by rememberSaveable { mutableStateOf<Dialog?>(null) }
    var gewaehltesMitglied by remember { mutableStateOf<Mitglied?>(null) }
    var teamMenue by remember { mutableStateOf(false) }
    var mehrMenue by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.laden() }
    LaunchedEffect(z.meldung) {
        z.meldung?.let {
            snackbar.showSnackbar(it)
            vm.meldungGezeigt()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val teams = z.ich?.teams.orEmpty()
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable(enabled = teams.size > 1) { teamMenue = true },
                        ) {
                            Text(z.team?.name ?: "Teamplaner", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (teams.size > 1) Icon(Icons.Default.ArrowDropDown, contentDescription = "Team wechseln")
                        }
                        DropdownMenu(expanded = teamMenue, onDismissRequest = { teamMenue = false }) {
                            teams.forEach { t ->
                                DropdownMenuItem(
                                    text = { Text("${t.name} (${t.rolle.anzeige})") },
                                    onClick = { teamMenue = false; vm.teamWaehlen(t.id) },
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = zumProfil) { Icon(Icons.Default.AccountCircle, contentDescription = "Profil") }
                    Box {
                        IconButton(onClick = { mehrMenue = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Mehr")
                        }
                        DropdownMenu(expanded = mehrMenue, onDismissRequest = { mehrMenue = false }) {
                            DropdownMenuItem(
                                text = { Text("Einladungscode einlösen") },
                                leadingIcon = { Icon(Icons.Default.GroupAdd, null) },
                                onClick = { mehrMenue = false; dialog = Dialog.CODE },
                            )
                            DropdownMenuItem(
                                text = { Text("Neues Team anlegen") },
                                leadingIcon = { Icon(Icons.Default.PersonAdd, null) },
                                onClick = { mehrMenue = false; dialog = Dialog.TEAM_NEU },
                            )
                            if (z.team != null) {
                                DropdownMenuItem(
                                    text = { Text("Team verlassen") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Logout, null) },
                                    onClick = { mehrMenue = false; dialog = Dialog.VERLASSEN },
                                )
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (z.rolle?.darfEinladen == true) {
                ExtendedFloatingActionButton(
                    onClick = { dialog = Dialog.EINLADEN },
                    icon = { Icon(Icons.Default.PersonAdd, null) },
                    text = { Text("Einladen") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { innen ->
        when {
            z.laedt && z.ich == null -> Box(Modifier.fillMaxSize().padding(innen), Alignment.Center) {
                CircularProgressIndicator()
            }
            z.team == null && z.ich != null -> OhneTeam(
                Modifier.padding(innen),
                codeEinloesen = { dialog = Dialog.CODE },
                teamAnlegen = { dialog = Dialog.TEAM_NEU },
            )
            else -> PullToRefreshBox(
                isRefreshing = z.aktualisiert,
                onRefresh = { vm.laden(pullToRefresh = true) },
                modifier = Modifier.fillMaxSize().padding(innen),
            ) {
                Mitgliederliste(
                    z = z,
                    mitgliedWaehlen = { gewaehltesMitglied = it },
                    einladungLoeschen = vm::einladungLoeschen,
                )
            }
        }
    }

    val team = z.team
    when (dialog) {
        Dialog.EINLADEN -> z.rolle?.let { r ->
            EinladenDialog(r, schliessen = { dialog = null }, erstellen = vm::einladen)
        }
        Dialog.CODE -> EingabeDialog(
            titel = "Einladungscode einlösen",
            beschriftung = "Code",
            knopf = "Beitreten",
            gross = true,
            schliessen = { dialog = null },
            bestaetigen = vm::codeEinloesen,
        )
        Dialog.TEAM_NEU -> EingabeDialog(
            titel = "Neues Team",
            beschriftung = "Name, z. B. „A-Jugend“",
            knopf = "Anlegen",
            schliessen = { dialog = null },
            bestaetigen = vm::teamAnlegen,
        )
        Dialog.VERLASSEN -> if (team != null) BestaetigenDialog(
            titel = "Team verlassen?",
            text = "Du bist danach kein Mitglied von „${team.name}“ mehr und brauchst für die Rückkehr einen neuen Code.",
            knopf = "Verlassen",
            schliessen = { dialog = null },
            bestaetigen = vm::austreten,
        )
        null -> Unit
    }
    gewaehltesMitglied?.let { m ->
        MitgliedDialog(
            mitglied = m,
            schliessen = { gewaehltesMitglied = null },
            rolleAendern = { vm.rolleAendern(m, it) },
            entfernen = { vm.entfernen(m) },
        )
    }
    z.neueEinladung?.let { e ->
        EinladungTeilen(e, team?.name.orEmpty(), z.server, schliessen = vm::einladungGezeigt)
    }
}

@Composable
private fun Mitgliederliste(
    z: TeamZustand,
    mitgliedWaehlen: (Mitglied) -> Unit,
    einladungLoeschen: (Einladung) -> Unit,
) {
    val kontext = LocalContext.current
    val istAdmin = z.rolle?.istAdmin == true
    val gruppen = z.mitglieder.groupBy { it.rolle }

    LazyColumn(Modifier.fillMaxSize()) {
        Rolle.entries.forEach { rolle ->
            val liste = gruppen[rolle].orEmpty()
            if (liste.isNotEmpty()) {
                item(key = "kopf-$rolle") { Ueberschrift(rollenTitel(rolle, liste.size)) }
                items(liste, key = { "m-${it.nutzerId}" }) { m ->
                    val ichSelbst = m.nutzerId == z.ich?.id
                    ListItem(
                        headlineContent = { Text(if (ichSelbst) "${m.name} (du)" else m.name) },
                        supportingContent = {
                            Text(listOfNotNull(m.email, "dabei seit ${datum(m.beigetretenAm)}").joinToString(" · "))
                        },
                        modifier = if (istAdmin && !ichSelbst) Modifier.clickable { mitgliedWaehlen(m) } else Modifier,
                    )
                }
            }
        }

        if (z.einladungen.isNotEmpty()) {
            item(key = "kopf-einladungen") {
                HorizontalDivider(Modifier.padding(top = 8.dp))
                Ueberschrift("Offene Einladungen")
            }
            items(z.einladungen, key = { "e-${it.id}" }) { e ->
                ListItem(
                    headlineContent = { Text(e.codeLesbar, fontFamily = FontFamily.Monospace) },
                    supportingContent = {
                        val nutzung = e.maxNutzungen?.let { "${e.nutzungen}/$it genutzt" } ?: "${e.nutzungen}× genutzt"
                        Text("${e.rolle.anzeige} · $nutzung · bis ${datum(e.gueltigBis)}")
                    },
                    trailingContent = {
                        Row {
                            IconButton(onClick = {
                                val senden = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, einladungsText(z.team?.name.orEmpty(), z.server, e))
                                }
                                kontext.startActivity(Intent.createChooser(senden, "Einladung teilen"))
                            }) { Icon(Icons.Default.Share, contentDescription = "Teilen") }
                            IconButton(onClick = { einladungLoeschen(e) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Löschen")
                            }
                        }
                    },
                )
            }
        }
        item { Spacer(Modifier.height(88.dp)) } // Platz für den Einladen-Knopf
    }
}

private fun rollenTitel(rolle: Rolle, anzahl: Int) = when (rolle) {
    Rolle.ADMIN -> if (anzahl == 1) "Admin" else "Admins"
    Rolle.TRAINER -> "Trainer"
    Rolle.SPIELER -> "Spieler ($anzahl)"
}

@Composable
private fun Ueberschrift(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun OhneTeam(modifier: Modifier, codeEinloesen: () -> Unit, teamAnlegen: () -> Unit) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Du bist noch in keinem Team.", style = MaterialTheme.typography.titleMedium)
        Text(
            "Löse einen Einladungscode ein oder lege selbst ein Team an.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = codeEinloesen, modifier = Modifier.fillMaxWidth()) { Text("Code einlösen") }
        OutlinedButton(onClick = teamAnlegen, modifier = Modifier.fillMaxWidth()) { Text("Team anlegen") }
    }
}
