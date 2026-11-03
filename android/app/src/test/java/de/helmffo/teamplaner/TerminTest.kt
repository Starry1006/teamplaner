package de.helmffo.teamplaner

import de.helmffo.teamplaner.daten.EigeneAntwort
import de.helmffo.teamplaner.daten.Status
import de.helmffo.teamplaner.daten.Termin
import de.helmffo.teamplaner.daten.Terminart
import de.helmffo.teamplaner.daten.Zaehler
import de.helmffo.teamplaner.ui.alsIso
import de.helmffo.teamplaner.ui.tagUeberschrift
import de.helmffo.teamplaner.ui.termine.Formular
import de.helmffo.teamplaner.ui.termine.antwortMoeglich
import de.helmffo.teamplaner.ui.zeitspanne
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone

class TerminTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun zeitzone() = TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
    }

    private fun termin(
        beginn: String = "2026-11-10T18:00:00Z",
        ende: String? = "2026-11-10T19:30:00Z",
        antwortBis: String = beginn,
        abgesagt: Boolean = false,
    ) = Termin(
        id = 1, serieId = "abc", art = Terminart.TRAINING, beginn = beginn, ende = ende, ort = "Sportplatz",
        treffenVorherMin = 15, antwortBis = antwortBis, abgesagt = abgesagt,
        eigene = EigeneAntwort(Status.OFFEN, automatisch = true), zaehler = Zaehler(),
    )

    @Test
    fun zeiten_werden_mit_offset_gesendet() {
        val z = ZonedDateTime.of(2026, 11, 10, 19, 0, 0, 0, ZoneId.of("Europe/Berlin"))
        assertEquals("2026-11-10T19:00:00+01:00", alsIso(z))
        assertEquals("2027-07-06T19:00:00+02:00", alsIso(z.withMonth(7).withYear(2027).withDayOfMonth(6)))
    }

    @Test
    fun zeitspanne_mit_treffpunkt() {
        assertEquals("19:00 – 20:30 · Treffen 18:45", zeitspanne(termin()))
    }

    @Test
    fun ueberschriften() {
        val heute = LocalDate.of(2026, 11, 3)
        assertEquals("Heute", tagUeberschrift(heute, heute))
        assertEquals("Morgen", tagUeberschrift(heute.plusDays(1), heute))
        assertEquals("Di., 10.11.", tagUeberschrift(LocalDate.of(2026, 11, 10), heute))
    }

    @Test
    fun ende_nach_mitternacht_ist_am_folgetag() {
        val f = Formular(datum = LocalDate.of(2026, 12, 12), beginn = LocalTime.of(20, 0), ende = LocalTime.of(1, 0))
        assertEquals(LocalDate.of(2026, 12, 13), f.endeZeit()!!.toLocalDate())
    }

    @Test
    fun neue_serie_nimmt_wochentag_des_datums() {
        val f = Formular(datum = LocalDate.of(2026, 11, 10), wiederholen = true, bis = LocalDate.of(2027, 6, 30))
        val neu = f.neu()
        assertEquals(listOf(1), neu.wiederholung!!.wochentage) // Dienstag
        assertEquals("2027-06-30", neu.wiederholung!!.bis)
        assertEquals("2026-11-10T19:00:00+01:00", neu.beginn)
        assertNull(neu.gegner) // nur bei Spielen
    }

    @Test
    fun aenderungen_enthalten_nur_geaendertes() {
        val alt = termin()
        val unveraendert = Formular.aus(alt)
        assertTrue(unveraendert.aenderungen(alt).isEmpty())

        val neu = unveraendert.copy(ort = "Halle", ende = null, beginn = LocalTime.of(19, 30))
        val a = neu.aenderungen(alt)
        assertEquals(setOf("ort", "ende", "beginn"), a.keys)
        assertEquals(JsonPrimitive("Halle"), a["ort"])
        assertEquals(JsonNull, a["ende"])
        assertEquals(JsonPrimitive("2026-11-10T19:30:00+01:00"), a["beginn"])
        // Leeres Feld löscht den Wert.
        assertEquals(JsonNull, unveraendert.copy(ort = "  ").aenderungen(alt)["ort"])
    }

    @Test
    fun antwortfrist() {
        val jetzt = Instant.parse("2026-11-10T10:00:00Z")
        val t = termin(antwortBis = "2026-11-09T18:00:00Z")
        assertFalse(t.antwortMoeglich(leitung = false, jetzt = jetzt))
        assertTrue(t.antwortMoeglich(leitung = true, jetzt = jetzt))
        assertTrue(termin().antwortMoeglich(false, jetzt))
        assertFalse(termin(abgesagt = true).antwortMoeglich(false, jetzt))
    }

    @Test
    fun termin_vom_server_lesen() {
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        val t = json.decodeFromString<Termin>(
            """{"id":5,"serie_id":null,"art":"spiel","titel":null,"beginn":"2026-11-15T14:00:00Z","ende":null,
               "treffen_vorher_min":60,"ort":"Auswärts","notiz":null,"gegner":"SV Musterstadt","heimspiel":false,
               "frist_stunden":48,"abgesagt":false,"absage_grund":null,"antwort_bis":"2026-11-13T14:00:00Z",
               "eigene":{"status":"zugesagt","grund":null,"automatisch":true},
               "zaehler":{"zugesagt":20,"abgesagt":3,"vielleicht":1,"offen":1},
               "teilnehmer":[{"nutzer_id":2,"name":"Ben","rolle":"spieler","status":"abgesagt","grund":"krank","automatisch":false,"geaendert_am":"2026-11-03T20:00:00Z"}]}""",
        )
        assertEquals("Spiel gegen SV Musterstadt", t.anzeigeTitel)
        assertFalse(t.istSerie)
        assertEquals(Status.ABGESAGT, t.teilnehmer.single().status)
    }
}
