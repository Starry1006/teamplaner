package de.helmffo.teamplaner.daten

import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Zugang zum Server: Anmeldung, Token-Verwaltung und die [TeamplanerApi] der aktuellen Sitzung.
 */
class Teamplaner(private val speicher: Sitzungsspeicher, private val scope: CoroutineScope) {

    val sitzung: Flow<Sitzung?> = speicher.sitzung
    val letzterServer: Flow<String?> = speicher.letzterServer
    val aktivesTeam: Flow<Int?> = speicher.aktivesTeam

    @Volatile private var token: String? = null
    @Volatile private var zwischenspeicher: Pair<String, TeamplanerApi>? = null

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        // Standardwerte (z. B. rolle = spieler) immer senden – nicht auf Server-Standards verlassen.
        encodeDefaults = true
    }

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(Interceptor { kette ->
            val anfrage = kette.request()
            val ohneToken = anfrage.url.encodedPath.let {
                it.endsWith("/auth/anmelden") || it.endsWith("/auth/registrieren")
            }
            val t = token
            val antwort = kette.proceed(
                if (t != null && !ohneToken) {
                    anfrage.newBuilder().header("Authorization", "Bearer $t").build()
                } else {
                    anfrage
                }
            )
            // Token abgelaufen oder auf dem Server widerrufen → lokal abmelden.
            if (antwort.code == 401 && !ohneToken && t != null && t == token) {
                token = null
                scope.launch { speicher.abmelden() }
            }
            antwort
        })
        .build()

    init {
        scope.launch { speicher.sitzung.collect { token = it?.token } }
    }

    private fun api(server: String): TeamplanerApi {
        zwischenspeicher?.let { (adresse, api) -> if (adresse == server) return api }
        val api = Retrofit.Builder()
            .baseUrl(server)
            .client(http)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(TeamplanerApi::class.java)
        zwischenspeicher = server to api
        return api
    }

    /** API der aktuellen Sitzung; wirft, wenn niemand angemeldet ist. */
    suspend fun api(): TeamplanerApi {
        val s = speicher.sitzung.first() ?: throw IllegalStateException("Nicht angemeldet")
        token = s.token
        return api(s.server)
    }

    suspend fun anmelden(server: String, email: String, passwort: String) {
        val adresse = serverAdresse(server)
        val antwort = api(adresse).anmelden(Anmeldung(email.trim(), passwort, geraet()))
        token = antwort.token
        speicher.anmelden(adresse, antwort.token)
    }

    suspend fun registrieren(server: String, code: String, name: String, email: String, passwort: String) {
        val adresse = serverAdresse(server)
        val antwort = api(adresse).registrieren(
            Registrierung(code.trim(), name.trim(), email.trim(), passwort, geraet())
        )
        token = antwort.token
        speicher.anmelden(adresse, antwort.token)
    }

    suspend fun abmelden() {
        try {
            versuche { api().abmelden() }
        } finally {
            token = null
            speicher.abmelden()
        }
    }

    suspend fun teamWaehlen(teamId: Int) = speicher.teamWaehlen(teamId)

    /** Zuletzt gewähltes Team, sonst das erste (und merkt es sich). */
    suspend fun aktivesTeam(ich: Ich): TeamKurz? {
        val wunsch = speicher.aktivesTeam.first()
        val team = ich.teams.firstOrNull { it.id == wunsch } ?: ich.teams.firstOrNull()
        if (team != null && team.id != wunsch) speicher.teamWaehlen(team.id)
        return team
    }

    private fun geraet(): String = "${Build.MANUFACTURER} ${Build.MODEL}".take(100)
}

/** „team.example.org“ → „https://team.example.org/“ (Retrofit verlangt den Schrägstrich am Ende). */
fun serverAdresse(eingabe: String): String {
    var s = eingabe.trim()
    require(s.isNotEmpty()) { "Bitte Serveradresse eingeben." }
    if (!s.startsWith("http://", ignoreCase = true) && !s.startsWith("https://", ignoreCase = true)) {
        s = "https://$s"
    }
    val url = s.toHttpUrlOrNull() ?: throw IllegalArgumentException("Ungültige Serveradresse.")
    return url.toString().let { if (it.endsWith("/")) it else "$it/" }
}
