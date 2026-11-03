package de.helmffo.teamplaner.daten

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import kotlinx.serialization.json.JsonObject

/** REST-Schnittstelle des Teamplaner-Servers (siehe server/teamplaner_server/routen). */
interface TeamplanerApi {
    @POST("auth/anmelden")
    suspend fun anmelden(@Body daten: Anmeldung): TokenAntwort

    @POST("auth/registrieren")
    suspend fun registrieren(@Body daten: Registrierung): TokenAntwort

    @POST("auth/abmelden")
    suspend fun abmelden()

    @GET("ich")
    suspend fun ich(): Ich

    @PATCH("ich")
    suspend fun profilAendern(@Body daten: ProfilAendern): Ich

    @PUT("ich/passwort")
    suspend fun passwortAendern(@Body daten: PasswortAendern)

    @POST("einladungen/einloesen")
    suspend fun codeEinloesen(@Body daten: CodeEinloesen): TeamKurz

    @POST("teams")
    suspend fun teamAnlegen(@Body daten: TeamNeu): TeamKurz

    @GET("teams/{team}/mitglieder")
    suspend fun mitglieder(@Path("team") teamId: Int): List<Mitglied>

    @PATCH("teams/{team}/mitglieder/{nutzer}")
    suspend fun rolleAendern(@Path("team") teamId: Int, @Path("nutzer") nutzerId: Int, @Body daten: RolleAendern)

    @DELETE("teams/{team}/mitglieder/{nutzer}")
    suspend fun mitgliedEntfernen(@Path("team") teamId: Int, @Path("nutzer") nutzerId: Int)

    @POST("teams/{team}/einladungen")
    suspend fun einladen(@Path("team") teamId: Int, @Body daten: EinladungNeu): Einladung

    @GET("teams/{team}/einladungen")
    suspend fun einladungen(@Path("team") teamId: Int): List<Einladung>

    @DELETE("teams/{team}/einladungen/{einladung}")
    suspend fun einladungLoeschen(@Path("team") teamId: Int, @Path("einladung") einladungId: Int)

    // --- Termine ---

    @GET("teams/{team}/termine")
    suspend fun termine(@Path("team") teamId: Int, @Query("vergangene") vergangene: Boolean = false): List<Termin>

    @POST("teams/{team}/termine")
    suspend fun terminAnlegen(@Path("team") teamId: Int, @Body daten: TerminNeu): List<Termin>

    @GET("teams/{team}/termine/{termin}")
    suspend fun termin(@Path("team") teamId: Int, @Path("termin") terminId: Int): Termin

    /** [aenderungen] enthält nur geänderte Felder; `null` löscht einen Wert. */
    @PATCH("teams/{team}/termine/{termin}")
    suspend fun terminAendern(
        @Path("team") teamId: Int,
        @Path("termin") terminId: Int,
        @Body aenderungen: JsonObject,
        @Query("bereich") bereich: Bereich = Bereich.EINZELN,
    ): Termin

    @DELETE("teams/{team}/termine/{termin}")
    suspend fun terminLoeschen(
        @Path("team") teamId: Int,
        @Path("termin") terminId: Int,
        @Query("bereich") bereich: Bereich = Bereich.EINZELN,
    )

    @PUT("teams/{team}/termine/{termin}/antwort")
    suspend fun antworten(@Path("team") teamId: Int, @Path("termin") terminId: Int, @Body daten: AntwortSetzen): Termin

    @GET("teams/{team}/standardantworten")
    suspend fun standardantworten(@Path("team") teamId: Int): Standardantworten

    @PUT("teams/{team}/standardantworten")
    suspend fun standardantwortenSetzen(@Path("team") teamId: Int, @Body daten: Standardantworten): Standardantworten

    @GET("teams/{team}/statistik")
    suspend fun statistik(@Path("team") teamId: Int, @Query("art") art: String = "training"): Statistik
}

/** Bei Serien: nur dieser Termin oder dieser und alle folgenden. */
enum class Bereich { EINZELN, FOLGENDE;

    override fun toString() = name.lowercase()
}
