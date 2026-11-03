package de.helmffo.teamplaner.daten

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.speicher by preferencesDataStore(name = "sitzung")

data class Sitzung(val server: String, val token: String)

/** Speichert Serveradresse, Anmelde-Token und das zuletzt gewählte Team (app-privat). */
class Sitzungsspeicher(private val context: Context) {
    private val serverKey = stringPreferencesKey("server")
    private val tokenKey = stringPreferencesKey("token")
    private val teamKey = intPreferencesKey("team")

    val sitzung: Flow<Sitzung?> = context.speicher.data.map { p ->
        val server = p[serverKey]
        val token = p[tokenKey]
        if (server != null && token != null) Sitzung(server, token) else null
    }

    /** Zuletzt verwendete Serveradresse – bleibt nach dem Abmelden erhalten. */
    val letzterServer: Flow<String?> = context.speicher.data.map { it[serverKey] }

    val aktivesTeam: Flow<Int?> = context.speicher.data.map { it[teamKey] }

    suspend fun anmelden(server: String, token: String) {
        context.speicher.edit {
            it[serverKey] = server
            it[tokenKey] = token
        }
    }

    suspend fun abmelden() {
        context.speicher.edit {
            it.remove(tokenKey)
            it.remove(teamKey)
        }
    }

    suspend fun teamWaehlen(teamId: Int) {
        context.speicher.edit { it[teamKey] = teamId }
    }
}
