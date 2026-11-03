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

// --- Termine (AP 2) -------------------------------------------------------------

@Serializable
enum class Terminart(val anzeige: String) {
    @SerialName("training") TRAINING("Training"),
    @SerialName("spiel") SPIEL("Spiel"),
    @SerialName("event") EVENT("Event"),
}

@Serializable
enum class Status(val anzeige: String) {
    @SerialName("zugesagt") ZUGESAGT("Zugesagt"),
    @SerialName("vielleicht") VIELLEICHT("Vielleicht"),
    @SerialName("abgesagt") ABGESAGT("Abgesagt"),
    @SerialName("offen") OFFEN("Keine Antwort"),
}

@Serializable
data class Wiederholung(val wochentage: List<Int>, val bis: String)

@Serializable
data class TerminNeu(
    val art: Terminart,
    val beginn: String,
    val ende: String? = null,
    val titel: String? = null,
    @SerialName("treffen_vorher_min") val treffenVorherMin: Int? = null,
    val ort: String? = null,
    val notiz: String? = null,
    val gegner: String? = null,
    val heimspiel: Boolean? = null,
    @SerialName("frist_stunden") val fristStunden: Int? = null,
    val wiederholung: Wiederholung? = null,
)

@Serializable
data class Zaehler(val zugesagt: Int = 0, val abgesagt: Int = 0, val vielleicht: Int = 0, val offen: Int = 0)

@Serializable
data class EigeneAntwort(val status: Status, val grund: String? = null, val automatisch: Boolean = false)

@Serializable
data class Teilnehmer(
    @SerialName("nutzer_id") val nutzerId: Int,
    val name: String,
    val rolle: Rolle,
    val status: Status,
    val grund: String? = null,
    val automatisch: Boolean = false,
    @SerialName("geaendert_am") val geaendertAm: String? = null,
)

/** Termin aus Liste (ohne [teilnehmer]) oder Detailansicht. */
@Serializable
data class Termin(
    val id: Int,
    @SerialName("serie_id") val serieId: String? = null,
    val art: Terminart,
    val titel: String? = null,
    val beginn: String,
    val ende: String? = null,
    @SerialName("treffen_vorher_min") val treffenVorherMin: Int? = null,
    val ort: String? = null,
    val notiz: String? = null,
    val gegner: String? = null,
    val heimspiel: Boolean? = null,
    @SerialName("frist_stunden") val fristStunden: Int? = null,
    val abgesagt: Boolean = false,
    @SerialName("absage_grund") val absageGrund: String? = null,
    @SerialName("antwort_bis") val antwortBis: String,
    val eigene: EigeneAntwort,
    val zaehler: Zaehler,
    val teilnehmer: List<Teilnehmer> = emptyList(),
) {
    val anzeigeTitel: String
        get() = titel ?: when (art) {
            Terminart.SPIEL -> gegner?.let { "Spiel gegen $it" } ?: "Spiel"
            else -> art.anzeige
        }

    val istSerie: Boolean get() = serieId != null
}

@Serializable
data class AntwortSetzen(
    val status: Status,
    val grund: String? = null,
    @SerialName("nutzer_id") val nutzerId: Int? = null,
)

@Serializable
data class Standardantworten(val training: Status, val spiel: Status, val event: Status)

@Serializable
data class StatistikZeile(
    @SerialName("nutzer_id") val nutzerId: Int,
    val name: String,
    val rolle: Rolle,
    val termine: Int,
    val zugesagt: Int,
    val abgesagt: Int,
    val vielleicht: Int,
    val offen: Int,
    val quote: Double,
)

@Serializable
data class Statistik(val art: Terminart, val von: String, val bis: String, val zeilen: List<StatistikZeile>)
