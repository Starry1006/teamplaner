package de.helmffo.teamplaner

import de.helmffo.teamplaner.daten.EinladungNeu
import de.helmffo.teamplaner.daten.Rolle
import de.helmffo.teamplaner.daten.RolleAendern
import de.helmffo.teamplaner.daten.TeamplanerApi
import de.helmffo.teamplaner.daten.meldung
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Prüft Pfade, Methoden und JSON-Felder der Schnittstelle gegen einen Schein-Server. */
class ApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: TeamplanerApi

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }
                .asConverterFactory("application/json".toMediaType()))
            .build()
            .create(TeamplanerApi::class.java)
    }

    @After
    fun stopp() = server.shutdown()

    @Test
    fun einladung_erstellen_sendet_snake_case() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """{"id":3,"code":"ABCDEFGH","rolle":"spieler","gueltig_bis":"2026-11-17T20:00:00Z","max_nutzungen":1,"nutzungen":0}""",
            ),
        )
        val e = api.einladen(7, EinladungNeu(maxNutzungen = 1))
        val anfrage = server.takeRequest()
        assertEquals("POST", anfrage.method)
        assertEquals("/teams/7/einladungen", anfrage.path)
        assertEquals("""{"rolle":"spieler","max_nutzungen":1}""", anfrage.body.readUtf8())
        assertEquals(1, e.maxNutzungen)
    }

    @Test
    fun antwort_ohne_inhalt_204() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        api.rolleAendern(1, 2, RolleAendern(Rolle.TRAINER))
        val anfrage = server.takeRequest()
        assertEquals("PATCH", anfrage.method)
        assertEquals("/teams/1/mitglieder/2", anfrage.path)
        assertEquals("""{"rolle":"trainer"}""", anfrage.body.readUtf8())
    }

    @Test
    fun fehler_detail_wird_angezeigt() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(409).setBody("""{"detail":"Das Team braucht mindestens einen Admin"}"""))
        val fehler = runCatching { api.mitgliedEntfernen(1, 1) }.exceptionOrNull()!!
        assertEquals("Das Team braucht mindestens einen Admin", fehler.meldung())
    }
}
