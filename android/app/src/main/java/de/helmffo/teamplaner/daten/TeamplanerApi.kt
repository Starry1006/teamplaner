package de.helmffo.teamplaner.daten

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

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
}
