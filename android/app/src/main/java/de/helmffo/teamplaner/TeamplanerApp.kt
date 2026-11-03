package de.helmffo.teamplaner

import android.app.Application
import de.helmffo.teamplaner.daten.Sitzungsspeicher
import de.helmffo.teamplaner.daten.Teamplaner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class TeamplanerApp : Application() {
    /** Einfaches manuelles DI: eine Instanz für die ganze App. */
    lateinit var teamplaner: Teamplaner
        private set

    override fun onCreate() {
        super.onCreate()
        teamplaner = Teamplaner(
            Sitzungsspeicher(this),
            CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    }
}
