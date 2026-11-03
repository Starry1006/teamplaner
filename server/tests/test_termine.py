from __future__ import annotations

from dataclasses import dataclass
from datetime import UTC, date, datetime, timedelta
from zoneinfo import ZoneInfo

import pytest
from conftest import Konto, einladen, registrieren
from sqlalchemy import update
from sqlalchemy.orm import Session

from teamplaner_server.modelle import Mitgliedschaft
from teamplaner_server.routen.termine import saison_beginn, serie_berechnen
from teamplaner_server.schemas_termine import Wiederholung

BERLIN = ZoneInfo("Europe/Berlin")


@dataclass
class Kader:
    team_id: int
    admin: Konto
    trainer: Konto
    ben: Konto
    cem: Konto

    def url(self, pfad: str = "") -> str:
        return f"/teams/{self.team_id}{pfad}"


@pytest.fixture
def kader(client, team_mit_admin) -> Kader:
    team_id, admin = team_mit_admin
    trainer = registrieren(client, einladen(client, team_id, admin.kopf, rolle="trainer"), "Tom Trainer", "tom@example.org")
    ben = registrieren(client, einladen(client, team_id, admin.kopf), "Ben", "ben@example.org")
    cem = registrieren(client, einladen(client, team_id, admin.kopf), "Cem", "cem@example.org")
    return Kader(team_id, admin, trainer, ben, cem)


def in_tagen(tage: int, stunde: int = 19) -> datetime:
    tag = (datetime.now(BERLIN) + timedelta(days=tage)).date()
    return datetime(tag.year, tag.month, tag.day, stunde, tzinfo=BERLIN)


def anlegen(client, k: Kader, **daten) -> list[dict]:
    daten.setdefault("art", "training")
    daten.setdefault("beginn", in_tagen(3).isoformat())
    r = client.post(k.url("/termine"), json=daten, headers=k.trainer.kopf)
    assert r.status_code == 201, r.text
    return r.json()


def antworten(client, k: Kader, termin_id: int, wer: Konto, status: str, **mehr):
    return client.put(k.url(f"/termine/{termin_id}/antwort"), json={"status": status, **mehr}, headers=wer.kopf)


# --- reine Funktionen ---------------------------------------------------------


def test_serie_behaelt_uhrzeit_ueber_zeitumstellung():
    # Di 19.10.2027 19:00 (Sommerzeit) bis Di 2.11.2027 (Winterzeit, Umstellung am 31.10.)
    beginn = datetime(2027, 10, 19, 19, 0, tzinfo=BERLIN)
    termine = serie_berechnen(beginn, beginn + timedelta(minutes=90), Wiederholung(wochentage=[1, 3], bis=date(2027, 11, 2)), BERLIN)
    lokal = [b.astimezone(BERLIN).strftime("%a %d.%m. %H:%M") for b, _ in termine]
    assert lokal == ["Tue 19.10. 19:00", "Thu 21.10. 19:00", "Tue 26.10. 19:00", "Thu 28.10. 19:00", "Tue 02.11. 19:00"]
    assert termine[0][0].hour == 17 and termine[-1][0].hour == 18  # UTC
    assert all(e - b == timedelta(minutes=90) for b, e in termine)


def test_serie_ohne_wochentage_nimmt_ersten_tag():
    beginn = datetime(2027, 3, 3, 18, 0, tzinfo=BERLIN)  # Mittwoch
    termine = serie_berechnen(beginn, None, Wiederholung(bis=date(2027, 3, 24)), BERLIN)
    assert len(termine) == 4 and all(e is None for _, e in termine)


def test_saisonbeginn():
    assert saison_beginn(date(2026, 11, 3)) == date(2026, 7, 1)
    assert saison_beginn(date(2027, 3, 1)) == date(2026, 7, 1)
    assert saison_beginn(date(2027, 7, 1)) == date(2027, 7, 1)


# --- Anlegen und Lesen --------------------------------------------------------


def test_nur_trainer_und_admin_legen_termine_an(client, kader):
    r = client.post(kader.url("/termine"), json={"art": "training", "beginn": in_tagen(2).isoformat()}, headers=kader.ben.kopf)
    assert r.status_code == 403
    r = client.post(kader.url("/termine"), json={"art": "training", "beginn": in_tagen(2).isoformat()}, headers=kader.admin.kopf)
    assert r.status_code == 201


def test_spiel_mit_allen_feldern(client, kader):
    beginn = in_tagen(5, 15)
    (t,) = anlegen(
        client, kader, art="spiel", beginn=beginn.isoformat(), ende=(beginn + timedelta(hours=2)).isoformat(),
        gegner="  SV Musterstadt ", heimspiel=False, treffen_vorher_min=60, ort="Sportplatz Musterstadt",
        frist_stunden=48, notiz="",
    )
    assert t["gegner"] == "SV Musterstadt" and t["heimspiel"] is False and t["notiz"] is None
    assert datetime.fromisoformat(t["beginn"]) == beginn
    assert datetime.fromisoformat(t["antwort_bis"]) == beginn - timedelta(hours=48)
    assert t["zaehler"] == {"zugesagt": 0, "abgesagt": 0, "vielleicht": 0, "offen": 4}
    assert t["eigene"] == {"status": "offen", "grund": None, "automatisch": True}


def test_ende_vor_beginn_abgelehnt(client, kader):
    b = in_tagen(2)
    r = client.post(kader.url("/termine"), json={"art": "event", "beginn": b.isoformat(), "ende": (b - timedelta(hours=1)).isoformat()}, headers=kader.trainer.kopf)
    assert r.status_code == 422


def test_serie_anlegen_und_liste(client, kader):
    start = in_tagen(1)
    serie = anlegen(client, kader, beginn=start.isoformat(), wiederholung={"wochentage": [1, 3], "bis": (start + timedelta(days=27)).date().isoformat()})
    assert len(serie) == 8
    assert len({t["serie_id"] for t in serie}) == 1
    liste = client.get(kader.url("/termine"), headers=kader.ben.kopf).json()
    assert [t["id"] for t in liste] == sorted(t["id"] for t in serie)
    beginne = [t["beginn"] for t in liste]
    assert beginne == sorted(beginne)


def test_fremdes_team_404(client, kader):
    (t,) = anlegen(client, kader)
    fremd = client.post("/teams", json={"name": "Anderes"}, headers=kader.ben.kopf).json()
    assert client.get(f"/teams/{fremd['id']}/termine/{t['id']}", headers=kader.ben.kopf).status_code == 404
    assert client.get(f"/teams/{kader.team_id}/termine", headers={"Authorization": "Bearer x"}).status_code == 401


# --- Antworten ----------------------------------------------------------------


def test_zu_und_absagen_mit_grund(client, kader):
    (t,) = anlegen(client, kader)
    assert antworten(client, kader, t["id"], kader.ben, "zugesagt").status_code == 200
    r = antworten(client, kader, t["id"], kader.cem, "abgesagt", grund=" krank ")
    d = r.json()
    assert d["zaehler"] == {"zugesagt": 1, "abgesagt": 1, "vielleicht": 0, "offen": 2}
    assert d["eigene"] == {"status": "abgesagt", "grund": "krank", "automatisch": False}

    # Ben sieht Cems Absage, aber nicht den Grund – der Trainer schon.
    sicht_ben = {p["name"]: p for p in client.get(kader.url(f"/termine/{t['id']}"), headers=kader.ben.kopf).json()["teilnehmer"]}
    assert sicht_ben["Cem"]["status"] == "abgesagt" and sicht_ben["Cem"]["grund"] is None
    sicht_tom = {p["name"]: p for p in client.get(kader.url(f"/termine/{t['id']}"), headers=kader.trainer.kopf).json()["teilnehmer"]}
    assert sicht_tom["Cem"]["grund"] == "krank"
    assert [p["name"] for p in sicht_tom.values()] == ["Anna Admin", "Tom Trainer", "Ben", "Cem"]

    # Antwort zurücknehmen.
    d = antworten(client, kader, t["id"], kader.ben, "offen").json()
    assert d["eigene"]["status"] == "offen" and d["zaehler"]["zugesagt"] == 0


def test_frist_gilt_fuer_spieler_nicht_fuer_trainer(client, kader):
    (t,) = anlegen(client, kader, beginn=(datetime.now(UTC) + timedelta(hours=2)).isoformat(), frist_stunden=24)
    r = antworten(client, kader, t["id"], kader.ben, "zugesagt")
    assert r.status_code == 409 and "frist" in r.json()["detail"]
    r = antworten(client, kader, t["id"], kader.trainer, "zugesagt", nutzer_id=kader.ben.nutzer_id)
    assert r.status_code == 200
    ben = next(p for p in r.json()["teilnehmer"] if p["name"] == "Ben")
    assert ben["status"] == "zugesagt"


def test_spieler_darf_nicht_fuer_andere_antworten(client, kader):
    (t,) = anlegen(client, kader)
    assert antworten(client, kader, t["id"], kader.ben, "abgesagt", nutzer_id=kader.cem.nutzer_id).status_code == 403
    assert antworten(client, kader, t["id"], kader.trainer, "zugesagt", nutzer_id=9999).status_code == 404


def test_abgesagter_termin(client, kader):
    (t,) = anlegen(client, kader)
    r = client.patch(kader.url(f"/termine/{t['id']}"), json={"abgesagt": True, "absage_grund": "Platz gesperrt"}, headers=kader.trainer.kopf)
    assert r.json()["abgesagt"] is True and r.json()["absage_grund"] == "Platz gesperrt"
    assert antworten(client, kader, t["id"], kader.ben, "zugesagt").status_code == 409
    r = client.patch(kader.url(f"/termine/{t['id']}"), json={"abgesagt": False}, headers=kader.trainer.kopf)
    assert r.json()["absage_grund"] is None


def test_standardantwort_zugesagt(client, kader):
    (t,) = anlegen(client, kader)
    werte = {"training": "zugesagt", "spiel": "offen", "event": "offen"}
    assert client.put(kader.url("/standardantworten"), json=werte, headers=kader.trainer.kopf).status_code == 403
    assert client.put(kader.url("/standardantworten"), json=werte, headers=kader.admin.kopf).json() == werte
    antworten(client, kader, t["id"], kader.cem, "abgesagt")
    d = client.get(kader.url(f"/termine/{t['id']}"), headers=kader.ben.kopf).json()
    assert d["zaehler"] == {"zugesagt": 3, "abgesagt": 1, "vielleicht": 0, "offen": 0}
    assert d["eigene"] == {"status": "zugesagt", "grund": None, "automatisch": True}
    assert client.get(kader.url("/standardantworten"), headers=kader.ben.kopf).json() == werte


# --- Ändern und Löschen von Serien --------------------------------------------


def _lokal(iso: str) -> str:
    return datetime.fromisoformat(iso).astimezone(BERLIN).strftime("%H:%M")


def test_serie_ab_hier_verschieben(client, kader):
    start = in_tagen(1)
    serie = anlegen(client, kader, beginn=start.isoformat(), ende=(start + timedelta(minutes=90)).isoformat(),
                    wiederholung={"bis": (start + timedelta(days=21)).date().isoformat()})
    assert len(serie) == 4
    dritter = serie[2]
    neu = datetime.fromisoformat(dritter["beginn"]) + timedelta(minutes=30)
    r = client.patch(kader.url(f"/termine/{dritter['id']}?bereich=folgende"),
                     json={"beginn": neu.isoformat(), "ort": "Halle"}, headers=kader.trainer.kopf)
    assert r.status_code == 200, r.text
    liste = client.get(kader.url("/termine"), headers=kader.ben.kopf).json()
    assert [_lokal(t["beginn"]) for t in liste] == ["19:00", "19:00", "19:30", "19:30"]
    assert [_lokal(t["ende"]) for t in liste] == ["20:30", "20:30", "21:00", "21:00"]
    assert [t["ort"] for t in liste] == [None, None, "Halle", "Halle"]

    # Nur einen Termin ändern
    client.patch(kader.url(f"/termine/{liste[0]['id']}"), json={"titel": "Torwarttraining"}, headers=kader.trainer.kopf)
    liste = client.get(kader.url("/termine"), headers=kader.ben.kopf).json()
    assert [t["titel"] for t in liste] == ["Torwarttraining", None, None, None]

    # Ende entfernen für alle folgenden
    client.patch(kader.url(f"/termine/{liste[1]['id']}?bereich=folgende"), json={"ende": None}, headers=kader.trainer.kopf)
    liste = client.get(kader.url("/termine"), headers=kader.ben.kopf).json()
    assert [t["ende"] is None for t in liste] == [False, True, True, True]


def test_serie_ab_hier_loeschen(client, kader):
    start = in_tagen(1)
    serie = anlegen(client, kader, beginn=start.isoformat(), wiederholung={"bis": (start + timedelta(days=21)).date().isoformat()})
    assert client.delete(kader.url(f"/termine/{serie[0]['id']}"), headers=kader.ben.kopf).status_code == 403
    assert client.delete(kader.url(f"/termine/{serie[2]['id']}?bereich=folgende"), headers=kader.trainer.kopf).status_code == 204
    assert client.delete(kader.url(f"/termine/{serie[0]['id']}"), headers=kader.trainer.kopf).status_code == 204
    assert [t["id"] for t in client.get(kader.url("/termine"), headers=kader.ben.kopf).json()] == [serie[1]["id"]]


# --- Vergangene Termine und Statistik -----------------------------------------


def test_vergangene_und_statistik(client, kader, engine):
    # Alle sind seit 30 Tagen dabei, Cem erst seit 5 Tagen.
    with Session(engine) as db:
        db.execute(update(Mitgliedschaft).values(beigetreten_am=datetime.now(UTC) - timedelta(days=30)))
        db.execute(update(Mitgliedschaft).where(Mitgliedschaft.nutzer_id == kader.cem.nutzer_id)
                   .values(beigetreten_am=datetime.now(UTC) - timedelta(days=5)))
        db.commit()

    t1, t2, t3 = (anlegen(client, kader, beginn=in_tagen(-tage).isoformat())[0] for tage in (10, 8, 2))
    abgesagt = anlegen(client, kader, beginn=in_tagen(-1).isoformat())[0]
    client.patch(kader.url(f"/termine/{abgesagt['id']}"), json={"abgesagt": True}, headers=kader.trainer.kopf)
    anlegen(client, kader, art="spiel", beginn=in_tagen(-3).isoformat())
    kommend = anlegen(client, kader)[0]

    # Spieler können nach Beginn nicht mehr antworten, der Trainer trägt nach.
    assert antworten(client, kader, t1["id"], kader.ben, "zugesagt").status_code == 409
    for t, ben in ((t1, "zugesagt"), (t2, "abgesagt"), (t3, "zugesagt")):
        antworten(client, kader, t["id"], kader.trainer, ben, nutzer_id=kader.ben.nutzer_id)
    antworten(client, kader, t3["id"], kader.trainer, "zugesagt", nutzer_id=kader.cem.nutzer_id)

    vergangen = client.get(kader.url("/termine?vergangene=true"), headers=kader.ben.kopf).json()
    assert kommend["id"] not in [t["id"] for t in vergangen]
    assert vergangen[0]["id"] == abgesagt["id"]  # neueste zuerst

    assert client.get(kader.url("/statistik"), headers=kader.ben.kopf).status_code == 403
    s = client.get(kader.url("/statistik"), headers=kader.trainer.kopf).json()
    zeilen = {z["name"]: z for z in s["zeilen"]}
    assert (zeilen["Ben"]["termine"], zeilen["Ben"]["zugesagt"], zeilen["Ben"]["abgesagt"]) == (3, 2, 1)
    assert zeilen["Ben"]["quote"] == pytest.approx(0.667)
    # Cem: nur das Training nach seinem Beitritt zählt.
    assert (zeilen["Cem"]["termine"], zeilen["Cem"]["zugesagt"], zeilen["Cem"]["quote"]) == (1, 1, 1.0)
    assert s["zeilen"][0]["name"] == "Cem"

    spiele = client.get(kader.url("/statistik?art=spiel"), headers=kader.trainer.kopf).json()
    assert {z["termine"] for z in spiele["zeilen"]} == {1}
