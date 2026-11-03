package de.helmffo.teamplaner.ui.termine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import de.helmffo.teamplaner.daten.Statistik
import de.helmffo.teamplaner.daten.Status
import de.helmffo.teamplaner.daten.Teamplaner
import de.helmffo.teamplaner.daten.Terminart
import de.helmffo.teamplaner.daten.meldung
import de.helmffo.teamplaner.daten.versuche
import de.helmffo.teamplaner.ui.fabrik
import de.helmffo.teamplaner.ui.zeitpunkt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

data class StatistikZustand(
    val art: Terminart = Terminart.TRAINING,
    val statistik: Statistik? = null,
    val laedt: Boolean = true,
    val fehler: String? = null,
)

class StatistikViewModel(private val teamplaner: Teamplaner, private val teamId: Int) : ViewModel() {
    private val _zustand = MutableStateFlow(StatistikZustand())
    val zustand: StateFlow<StatistikZustand> = _zustand

    fun laden(art: Terminart = _zustand.value.art) {
        _zustand.update { it.copy(art = art, laedt = true, fehler = null) }
        viewModelScope.launch {
            versuche { teamplaner.api().statistik(teamId, art.name.lowercase()) }
                .onSuccess { s -> _zustand.update { it.copy(statistik = s, laedt = false) } }
                .onFailure { e -> _zustand.update { it.copy(fehler = e.meldung(), laedt = false) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatistikScreen(
    teamId: Int,
    zurueck: () -> Unit,
    vm: StatistikViewModel = viewModel(factory = fabrik { StatistikViewModel(it, teamId) }),
) {
    val z by vm.zustand.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.laden() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Beteiligung") },
                navigationIcon = { IconButton(onClick = zurueck) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
            )
        },
    ) { innen ->
        Column(Modifier.fillMaxSize().padding(innen)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                listOf(Terminart.TRAINING, Terminart.SPIEL).forEachIndexed { i, art ->
                    SegmentedButton(
                        selected = z.art == art,
                        onClick = { vm.laden(art) },
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                    ) { Text(if (art == Terminart.TRAINING) "Training" else "Spiele") }
                }
            }
            val s = z.statistik
            when {
                z.laedt && s == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                z.fehler != null -> Text(z.fehler!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                s != null -> {
                    val f = DateTimeFormatter.ofPattern("dd.MM.yyyy")
                    Text(
                        "Seit ${zeitpunkt(s.von).format(f)} · nur vergangene, nicht abgesagte Termine ab Beitritt",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    if (s.zeilen.all { it.termine == 0 }) {
                        Text(
                            "Noch keine vergangenen Termine in dieser Saison.",
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                        )
                    }
                    LazyColumn {
                        items(s.zeilen, key = { it.nutzerId }) { zeile ->
                            ListItem(
                                headlineContent = { Text(zeile.name) },
                                supportingContent = {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        LinearProgressIndicator(
                                            progress = { zeile.quote.toFloat() },
                                            color = Status.ZUGESAGT.farbe(),
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        Text("${zeile.zugesagt} von ${zeile.termine} · ${zeile.abgesagt} abgesagt · ${zeile.offen} ohne Antwort")
                                    }
                                },
                                trailingContent = {
                                    Text("${(zeile.quote * 100).roundToInt()} %", style = MaterialTheme.typography.titleMedium)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
