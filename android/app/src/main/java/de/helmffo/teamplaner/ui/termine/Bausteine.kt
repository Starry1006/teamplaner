package de.helmffo.teamplaner.ui.termine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import de.helmffo.teamplaner.daten.Status
import de.helmffo.teamplaner.daten.Terminart
import de.helmffo.teamplaner.daten.Zaehler

fun Terminart.symbol(): ImageVector = when (this) {
    Terminart.TRAINING -> Icons.AutoMirrored.Filled.DirectionsRun
    Terminart.SPIEL -> Icons.Default.SportsSoccer
    Terminart.EVENT -> Icons.Default.Celebration
}

fun Status.symbol(): ImageVector = when (this) {
    Status.ZUGESAGT -> Icons.Default.Check
    Status.ABGESAGT -> Icons.Default.Close
    Status.VIELLEICHT, Status.OFFEN -> Icons.Default.QuestionMark
}

/** Ampelfarben – in hell und dunkel gut lesbar. */
@Composable
fun Status.farbe(): Color {
    val dunkel = MaterialTheme.colorScheme.background.red < 0.5f
    return when (this) {
        Status.ZUGESAGT -> if (dunkel) Color(0xFF81C784) else Color(0xFF2E7D32)
        Status.VIELLEICHT -> if (dunkel) Color(0xFFFFD54F) else Color(0xFFB26A00)
        Status.ABGESAGT -> if (dunkel) Color(0xFFE57373) else Color(0xFFC62828)
        Status.OFFEN -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

/** Drei Knöpfe ✓ ? ✗ für die eigene Antwort. */
@Composable
fun AntwortKnoepfe(aktuell: Status, aktiv: Boolean, waehlen: (Status) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(Status.ZUGESAGT, Status.VIELLEICHT, Status.ABGESAGT).forEach { s ->
            val farbe = s.farbe()
            FilledTonalIconToggleButton(
                checked = aktuell == s,
                onCheckedChange = { waehlen(if (it) s else Status.OFFEN) },
                enabled = aktiv,
                colors = IconButtonDefaults.filledTonalIconToggleButtonColors(
                    checkedContainerColor = farbe,
                    checkedContentColor = MaterialTheme.colorScheme.surface,
                ),
            ) { Icon(s.symbol(), contentDescription = s.anzeige) }
        }
    }
}

/** „✓ 14  ? 2  ✗ 3  · 6 offen“ */
@Composable
fun Zaehlerzeile(z: Zaehler) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(Status.ZUGESAGT to z.zugesagt, Status.VIELLEICHT to z.vielleicht, Status.ABGESAGT to z.abgesagt).forEach { (s, n) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(s.symbol(), contentDescription = s.anzeige, tint = s.farbe(), modifier = Modifier.size(16.dp))
                Text(" $n", style = MaterialTheme.typography.labelLarge)
            }
        }
        if (z.offen > 0) {
            Text(
                "${z.offen} offen",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

val SCHNELLGRUENDE = listOf("Krank", "Verletzt", "Arbeit", "Schule", "Urlaub", "Privat")

/** Fragt beim Absagen (oder „Vielleicht“) optional nach dem Grund. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GrundDialog(status: Status, name: String? = null, schliessen: () -> Unit, bestaetigen: (String?) -> Unit) {
    var grund by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = schliessen,
        title = { Text(if (name != null) "${status.anzeige}: $name" else status.anzeige) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SCHNELLGRUENDE.forEach { g -> SuggestionChip(onClick = { grund = g }, label = { Text(g) }) }
                }
                OutlinedTextField(
                    value = grund,
                    onValueChange = { grund = it.take(200) },
                    label = { Text("Grund (optional, nur für Trainer sichtbar)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { bestaetigen(grund.trim().ifEmpty { null }); schliessen() }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = schliessen) { Text("Abbrechen") } },
    )
}
