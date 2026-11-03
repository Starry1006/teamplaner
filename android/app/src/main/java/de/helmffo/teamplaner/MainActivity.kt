package de.helmffo.teamplaner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.helmffo.teamplaner.daten.Teamplaner
import de.helmffo.teamplaner.ui.anmeldung.AnmeldungScreen
import de.helmffo.teamplaner.ui.profil.ProfilScreen
import de.helmffo.teamplaner.ui.team.TeamScreen
import de.helmffo.teamplaner.ui.termine.StatistikScreen
import de.helmffo.teamplaner.ui.termine.TerminBearbeitenScreen
import de.helmffo.teamplaner.ui.termine.TerminDetailScreen
import de.helmffo.teamplaner.ui.termine.TermineScreen
import de.helmffo.teamplaner.ui.theme.TeamplanerTheme
import kotlinx.coroutines.flow.map

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val teamplaner = (application as TeamplanerApp).teamplaner
        setContent {
            TeamplanerTheme {
                Surface(Modifier.fillMaxSize()) { Wurzel(teamplaner) }
            }
        }
    }
}

@Composable
private fun Wurzel(teamplaner: Teamplaner) {
    val angemeldetFlow = remember(teamplaner) { teamplaner.sitzung.map { it != null } }
    val angemeldet by angemeldetFlow.collectAsStateWithLifecycle(initialValue = null)
    when (angemeldet) {
        null -> Box(Modifier.fillMaxSize()) // Sitzung wird geladen (nur ein kurzer Moment)
        false -> AnmeldungScreen()
        true -> Hauptbereich()
    }
}

private val TEAM = navArgument("team") { type = NavType.IntType }
private val ID = navArgument("id") { type = NavType.IntType }
private fun NavBackStackEntry.zahl(name: String) = arguments!!.getInt(name)

@Composable
private fun Hauptbereich() {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "haupt") {
        composable("haupt") { Reiter(nav) }
        composable("profil") { ProfilScreen(zurueck = { nav.popBackStack() }) }
        composable("termin/{team}/{id}", listOf(TEAM, ID)) { e ->
            val team = e.zahl("team")
            val id = e.zahl("id")
            TerminDetailScreen(
                teamId = team,
                terminId = id,
                zurueck = { nav.popBackStack() },
                bearbeiten = { nav.navigate("bearbeiten/$team/$id") },
            )
        }
        composable("neu/{team}", listOf(TEAM)) { e ->
            TerminBearbeitenScreen(teamId = e.zahl("team"), terminId = null, fertig = { nav.popBackStack() })
        }
        composable("bearbeiten/{team}/{id}", listOf(TEAM, ID)) { e ->
            TerminBearbeitenScreen(teamId = e.zahl("team"), terminId = e.zahl("id"), fertig = { nav.popBackStack() })
        }
        composable("statistik/{team}", listOf(TEAM)) { e ->
            StatistikScreen(teamId = e.zahl("team"), zurueck = { nav.popBackStack() })
        }
    }
}

/** Untere Navigation: Termine (Start) und Team. Kasse und Umfragen folgen in AP 3/4. */
@Composable
private fun Reiter(nav: NavHostController) {
    var reiter by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = reiter == 0,
                    onClick = { reiter = 0 },
                    icon = { Icon(Icons.Default.CalendarMonth, null) },
                    label = { Text("Termine") },
                )
                NavigationBarItem(
                    selected = reiter == 1,
                    onClick = { reiter = 1 },
                    icon = { Icon(Icons.Default.Groups, null) },
                    label = { Text("Team") },
                )
            }
        },
    ) { innen ->
        // Oben kümmern sich die TopAppBars der Reiter um die Statusleiste.
        Box(Modifier.padding(bottom = innen.calculateBottomPadding())) {
            when (reiter) {
                0 -> TermineScreen(
                    oeffnen = { team, id -> nav.navigate("termin/$team/$id") },
                    neu = { team -> nav.navigate("neu/$team") },
                    statistik = { team -> nav.navigate("statistik/$team") },
                    profilSymbol = {
                        IconButton(onClick = { nav.navigate("profil") }) { Icon(Icons.Default.AccountCircle, "Profil") }
                    },
                )
                else -> TeamScreen(zumProfil = { nav.navigate("profil") })
            }
        }
    }
}
