package de.helmffo.teamplaner.ui.anmeldung

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.helmffo.teamplaner.ui.PasswortFeld
import de.helmffo.teamplaner.ui.fabrik

@Composable
fun AnmeldungScreen(vm: AnmeldungViewModel = viewModel(factory = fabrik { AnmeldungViewModel(it) })) {
    val z by vm.zustand.collectAsStateWithLifecycle()
    val feld = Modifier.fillMaxWidth()

    Scaffold { innen ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innen)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.widthIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Teamplaner", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "Termine, Mitglieder, Kasse und Umfragen für dein Team.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))

                SingleChoiceSegmentedButtonRow(feld) {
                    SegmentedButton(
                        selected = !z.registrieren,
                        onClick = { vm.aendern { it.copy(registrieren = false) } },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) { Text("Anmelden") }
                    SegmentedButton(
                        selected = z.registrieren,
                        onClick = { vm.aendern { it.copy(registrieren = true) } },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                    ) { Text("Neu mit Code") }
                }

                OutlinedTextField(
                    value = z.server,
                    onValueChange = { v -> vm.aendern { it.copy(server = v) } },
                    label = { Text("Server") },
                    placeholder = { Text("team.example.org") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    modifier = feld,
                )
                if (z.registrieren) {
                    OutlinedTextField(
                        value = z.code,
                        onValueChange = { v -> vm.aendern { it.copy(code = v.uppercase()) } },
                        label = { Text("Einladungscode") },
                        placeholder = { Text("ABCD-EFGH") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = feld,
                    )
                    OutlinedTextField(
                        value = z.name,
                        onValueChange = { v -> vm.aendern { it.copy(name = v) } },
                        label = { Text("Dein Name") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = feld,
                    )
                }
                OutlinedTextField(
                    value = z.email,
                    onValueChange = { v -> vm.aendern { it.copy(email = v) } },
                    label = { Text("E-Mail") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    modifier = feld,
                )
                PasswortFeld(
                    wert = z.passwort,
                    aendern = { v -> vm.aendern { it.copy(passwort = v) } },
                    beschriftung = if (z.registrieren) "Passwort (mind. 8 Zeichen)" else "Passwort",
                    modifier = feld,
                )

                z.fehler?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }

                Button(onClick = vm::absenden, enabled = !z.laeuft, modifier = feld.height(48.dp)) {
                    if (z.laeuft) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(if (z.registrieren) "Konto anlegen und beitreten" else "Anmelden")
                    }
                }
                if (z.registrieren) {
                    Text(
                        "Den Code bekommst du von deinem Trainer oder Admin.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
