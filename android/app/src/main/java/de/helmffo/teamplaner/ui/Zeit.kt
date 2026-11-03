package de.helmffo.teamplaner.ui

import de.helmffo.teamplaner.daten.Termin
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DEUTSCH = Locale.GERMANY
private val UHRZEIT = DateTimeFormatter.ofPattern("HH:mm", DEUTSCH)
private val TAG_KURZ = DateTimeFormatter.ofPattern("EEE, dd.MM.", DEUTSCH)
private val TAG_LANG = DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy", DEUTSCH)
private val SENDEN = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")

fun zeitpunkt(iso: String, zone: ZoneId = ZoneId.systemDefault()): ZonedDateTime =
    OffsetDateTime.parse(iso).atZoneSameInstant(zone)

/** Für den Server: immer mit Sekunden und Zeitzone. */
fun alsIso(z: ZonedDateTime): String = z.format(SENDEN)

fun uhrzeit(z: ZonedDateTime): String = z.format(UHRZEIT)

/** „Heute“, „Morgen“ oder „Di, 04.11.“ (mit Jahr, wenn nicht im laufenden Jahr). */
fun tagUeberschrift(tag: LocalDate, heute: LocalDate = LocalDate.now()): String = when (tag) {
    heute -> "Heute"
    heute.plusDays(1) -> "Morgen"
    heute.minusDays(1) -> "Gestern"
    else -> tag.format(if (tag.year == heute.year) TAG_KURZ else DateTimeFormatter.ofPattern("EEE, dd.MM.yyyy", DEUTSCH))
}

fun tagLang(z: ZonedDateTime): String = z.format(TAG_LANG)

/** „19:00 – 20:30 · Treffen 18:30“ */
fun zeitspanne(t: Termin): String {
    val b = zeitpunkt(t.beginn)
    val teile = mutableListOf(uhrzeit(b) + (t.ende?.let { " – " + uhrzeit(zeitpunkt(it)) } ?: ""))
    t.treffenVorherMin?.takeIf { it > 0 }?.let { teile += "Treffen " + uhrzeit(b.minusMinutes(it.toLong())) }
    return teile.joinToString(" · ")
}

fun istVorbei(iso: String, jetzt: Instant = Instant.now()): Boolean = OffsetDateTime.parse(iso).toInstant() <= jetzt

/** Wochentage wie beim Server: 0 = Montag … 6 = Sonntag. */
val WOCHENTAGE = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
