package de.helmffo.teamplaner.ui.team

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import de.helmffo.teamplaner.daten.Einladung
import de.helmffo.teamplaner.daten.Mitglied
import de.helmffo.teamplaner.daten.Rolle
import de.helmffo.teamplaner.ui.datum

@Composable
private fun RollenAuswahl(rollen: List<Rolle>, gewaehlt: Rolle, waehlen: (Rolle) -> Unit) {
    Column {
        rollen.forEach { r ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(selected = r == gewaehlt, onClick = { waehlen(r) }, role = Role.RadioButton),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = r == gewaehlt, onClick = null)
                Text(r.anzeige)
            }
        }
    }
}

@Composable
fun EinladenDialog(eigeneRolle: Rolle, schliessen: () -> Unit, erstellen: (Rolle, Boolean) -> Unit) {
    var rolle by rememberSaveable { mutableStateOf(Rolle.SPIELER) }
    var einmalig by rememberSaveable { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = schliessen,
        title = { Text("Mitglied einladen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (eigeneRolle.istAdmin) {
                    Text("Rolle", style = MaterialTheme.typography.labelLarge)
                    RollenAuswahl(Rolle.entries.reversed(), rolle) { rolle = it }
                } else {
                    Text("Trainer können Spieler einladen.")
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(value = einmalig, onValueChange = { einmalig = it }, role = Role.Checkbox),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = einmalig, onCheckedChange = null)
                    Text("Nur für eine Person")
                }
                Text(
                    if (einmalig) "Der Code funktioniert genau einmal."
                    else "Mehrfach nutzbar – z. B. für die Mannschafts-Gruppe. 14 Tage gültig.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { erstellen(rolle, einmalig); schliessen() }) { Text("Code erstellen") } },
        dismissButton = { TextButton(onClick = schliessen) { Text("Abbrechen") } },
    )
}

fun einladungsText(teamName: String, server: String, einladung: Einladung): String =
    "Du bist zu „$teamName“ im Teamplaner eingeladen.\n\n" +
        "1. Teamplaner-App öffnen und „Neu mit Code“ wählen\n" +
        "2. Server: ${server.removePrefix("https://").removeSuffix("/")}\n" +
        "3. Code: ${einladung.codeLesbar}\n\n" +
        "Der Code ist bis ${datum(einladung.gueltigBis)} gültig."

@Composable
fun EinladungTeilen(einladung: Einladung, teamName: String, server: String, schliessen: () -> Unit) {
    val kontext = LocalContext.current
    AlertDialog(
        onDismissRequest = schliessen,
        title = { Text("Einladungscode") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    einladung.codeLesbar,
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.Monospace,
                )
                Text("Rolle: ${einladung.rolle.anzeige} · gültig bis ${datum(einladung.gueltigBis)}")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val senden = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, einladungsText(teamName, server, einladung))
                }
                kontext.startActivity(Intent.createChooser(senden, "Einladung teilen"))
                schliessen()
            }) { Text("Teilen") }
        },
        dismissButton = { TextButton(onClick = schliessen) { Text("Schließen") } },
    )
}

@Composable
fun MitgliedDialog(
    mitglied: Mitglied,
    schliessen: () -> Unit,
    rolleAendern: (Rolle) -> Unit,
    entfernen: () -> Unit,
) {
    var rolle by rememberSaveable { mutableStateOf(mitglied.rolle) }
    var sicher by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = schliessen,
        title = { Text(mitglied.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                mitglied.email?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text("Rolle", style = MaterialTheme.typography.labelLarge)
                RollenAuswahl(Rolle.entries.reversed(), rolle) { rolle = it }
                TextButton(onClick = { if (sicher) { entfernen(); schliessen() } else sicher = true }) {
                    Text(
                        if (sicher) "Wirklich aus dem Team entfernen?" else "Aus dem Team entfernen",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = rolle != mitglied.rolle,
                onClick = { rolleAendern(rolle); schliessen() },
            ) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = schliessen) { Text("Abbrechen") } },
    )
}

@Composable
fun EingabeDialog(
    titel: String,
    beschriftung: String,
    knopf: String,
    schliessen: () -> Unit,
    gross: Boolean = false,
    bestaetigen: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = schliessen,
        title = { Text(titel) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = if (gross) it.uppercase() else it },
                label = { Text(beschriftung) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = if (gross) KeyboardCapitalization.Characters else KeyboardCapitalization.Sentences,
                ),
            )
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { bestaetigen(text); schliessen() }) { Text(knopf) }
        },
        dismissButton = { TextButton(onClick = schliessen) { Text("Abbrechen") } },
    )
}

@Composable
fun BestaetigenDialog(titel: String, text: String, knopf: String, schliessen: () -> Unit, bestaetigen: () -> Unit) {
    AlertDialog(
        onDismissRequest = schliessen,
        title = { Text(titel) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { bestaetigen(); schliessen() }) { Text(knopf) } },
        dismissButton = { TextButton(onClick = schliessen) { Text("Abbrechen") } },
    )
}

