package de.helmffo.teamplaner

import de.helmffo.teamplaner.daten.Einladung
import de.helmffo.teamplaner.daten.Ich
import de.helmffo.teamplaner.daten.Mitglied
import de.helmffo.teamplaner.daten.Rolle
import de.helmffo.teamplaner.daten.serverAdresse
import de.helmffo.teamplaner.daten.serverMeldung
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class DatenTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun serveradresse_wird_vervollstaendigt() {
        assertEquals("https://team.example.org/", serverAdresse(" team.example.org "))
        assertEquals("http://192.168.1.10:8000/", serverAdresse("http://192.168.1.10:8000"))
        assertEquals("https://example.org/teamplaner/", serverAdresse("https://example.org/teamplaner"))
        assertThrows(IllegalArgumentException::class.java) { serverAdresse("  ") }
        assertThrows(IllegalArgumentException::class.java) { serverAdresse("https://") }
    }

    @Test
    fun fehlermeldungen_vom_server() {
        assertEquals("E-Mail oder Passwort falsch", serverMeldung(401, """{"detail":"E-Mail oder Passwort falsch"}"""))
        assertEquals(
            "Bitte Eingaben prüfen: value is not a valid email address",
            serverMeldung(422, """{"detail":[{"loc":["body","email"],"msg":"value is not a valid email address"}]}"""),
        )
        assertEquals("Fehler auf dem Server (502).", serverMeldung(502, "<html>Bad Gateway</html>"))
        assertEquals("Bitte neu anmelden.", serverMeldung(401, null))
    }

    @Test
    fun antworten_des_servers_werden_gelesen() {
        // Beispiele wie sie der FastAPI-Server liefert (Feldnamen mit Unterstrich).
        val ich = json.decodeFromString<Ich>(
            """{"id":1,"name":"Thomas","email":"t@example.org","teams":[{"id":1,"name":"1. Herren","rolle":"admin"}]}""",
        )
        assertEquals(Rolle.ADMIN, ich.teams.single().rolle)

        val m = json.decodeFromString<Mitglied>(
            """{"nutzer_id":2,"name":"Ben","email":null,"rolle":"spieler","beigetreten_am":"2026-11-03T20:48:47.419511Z"}""",
        )
        assertEquals(2, m.nutzerId)
        assertNull(m.email)

        val e = json.decodeFromString<Einladung>(
            """{"id":1,"code":"ES3U9N8D","rolle":"trainer","gueltig_bis":"2026-11-17T20:00:00Z","max_nutzungen":null,"nutzungen":0}""",
        )
        assertEquals("ES3U-9N8D", e.codeLesbar)
        assertNull(e.maxNutzungen)
    }
}
