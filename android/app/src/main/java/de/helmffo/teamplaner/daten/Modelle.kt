package de.helmffo.teamplaner.daten

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Rolle(val anzeige: String) {
    @SerialName("admin") ADMIN("Admin"),
    @SerialName("trainer") TRAINER("Trainer"),
    @SerialName("spieler") SPIELER("Spieler");

    val darfEinladen: Boolean get() = this == ADMIN || this == TRAINER
    val istAdmin: Boolean get() = this == ADMIN
}

@Serializable
data class Anmeldung(val email: String, val passwort: String, val geraet: String? = null)

@Serializable
data class Registrierung(
    val code: String,
    val name: String,
    val email: String,
    val passwort: String,
    val geraet: String? = null,
)

@Serializable
data class TokenAntwort(val token: String, @SerialName("gueltig_bis") val gueltigBis: String)

@Serializable
data class TeamKurz(val id: Int, val name: String, val rolle: Rolle)

@Serializable
data class Ich(val id: Int, val name: String, val email: String, val teams: List<TeamKurz>)

@Serializable
data class ProfilAendern(val name: String)

@Serializable
data class PasswortAendern(val alt: String, val neu: String)

@Serializable
data class TeamNeu(val name: String)

@Serializable
data class Mitglied(
    @SerialName("nutzer_id") val nutzerId: Int,
    val name: String,
    val email: String? = null,
    val rolle: Rolle,
    @SerialName("beigetreten_am") val beigetretenAm: String,
)

@Serializable
data class RolleAendern(val rolle: Rolle)

@Serializable
data class EinladungNeu(
    val rolle: Rolle = Rolle.SPIELER,
    @SerialName("gueltig_tage") val gueltigTage: Int? = null,
    @SerialName("max_nutzungen") val maxNutzungen: Int? = null,
)

@Serializable
data class Einladung(
    val id: Int,
    val code: String,
    val rolle: Rolle,
    @SerialName("gueltig_bis") val gueltigBis: String,
    @SerialName("max_nutzungen") val maxNutzungen: Int? = null,
    val nutzungen: Int,
) {
    /** „ABCD-EFGH“ – besser lesbar und abtippbar. */
    val codeLesbar: String get() = if (code.length == 8) "${code.take(4)}-${code.drop(4)}" else code
}

@Serializable
data class CodeEinloesen(val code: String)
