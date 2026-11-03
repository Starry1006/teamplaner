package de.helmffo.teamplaner.daten

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/** Verständliche Fehlermeldung für die Oberfläche. */
fun Throwable.meldung(): String = when (this) {
    is HttpException -> serverMeldung(code(), response()?.errorBody()?.string())
    is IOException -> "Server nicht erreichbar – Internetverbindung und Serveradresse prüfen."
    is SerializationException -> "Unerwartete Antwort vom Server."
    is IllegalArgumentException -> message ?: "Ungültige Eingabe."
    else -> "Unbekannter Fehler: ${this::class.simpleName}"
}

/** Wertet `{"detail": ...}` von FastAPI aus. */
internal fun serverMeldung(status: Int, body: String?): String {
    val detail = try {
        body?.let { (Json.parseToJsonElement(it) as? JsonObject)?.get("detail") }
    } catch (_: SerializationException) {
        null
    }
    return when {
        detail is JsonPrimitive && detail.isString -> detail.content
        status == 422 -> "Bitte Eingaben prüfen" + ((detail as? JsonArray)?.firstOrNull()
            ?.let { (it as? JsonObject)?.get("msg")?.jsonPrimitive?.content }?.let { ": $it" } ?: ".")
        status == 401 -> "Bitte neu anmelden."
        status >= 500 -> "Fehler auf dem Server ($status)."
        else -> "Anfrage fehlgeschlagen ($status)."
    }
}

/** Führt [block] aus und liefert das Ergebnis – Abbrüche von Coroutinen werden durchgereicht. */
suspend fun <T> versuche(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
