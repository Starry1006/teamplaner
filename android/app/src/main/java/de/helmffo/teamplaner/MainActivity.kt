package de.helmffo.teamplaner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import de.helmffo.teamplaner.daten.Teamplaner
import de.helmffo.teamplaner.ui.anmeldung.AnmeldungScreen
import de.helmffo.teamplaner.ui.profil.ProfilScreen
import de.helmffo.teamplaner.ui.team.TeamScreen
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

@Composable
private fun Hauptbereich() {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "team") {
        composable("team") { TeamScreen(zumProfil = { nav.navigate("profil") }) }
        composable("profil") { ProfilScreen(zurueck = { nav.popBackStack() }) }
    }
}
