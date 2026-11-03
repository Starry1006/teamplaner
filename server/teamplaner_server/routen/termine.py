"""Termine (Training, Spiel, Event), Serien, Zu-/Absagen und Trainingsbeteiligung."""

from __future__ import annotations

import uuid
from datetime import UTC, date, datetime, time, timedelta
from enum import StrEnum
from zoneinfo import ZoneInfo

from fastapi import APIRouter, HTTPException, Query, Request, Response, status
from sqlalchemy import select
from sqlalchemy.orm import Session

from ..abhaengigkeiten import AktNutzer, Db, mitgliedschaft_holen, rolle_verlangen
from ..datenbank import als_utc, jetzt
from ..modelle import Antwort, Mitgliedschaft, Nutzer, Rolle, Status, Team, Termin, Terminart
from ..schemas_termine import (
    AntwortSetzen,
    EigeneAntwort,
    Standardantworten,
    Statistik,
    StatistikZeile,
    Teilnehmer,
    TerminAendern,
    TerminAusgabe,
    TerminDetail,
    TerminNeu,
    Wiederholung,
    Zaehler,
)

router = APIRouter(prefix="/teams/{team_id}", tags=["Termine"])

MAX_SERIE = 200
MAX_SERIE_TAGE = 400
_ROLLEN_REIHENFOLGE = {Rolle.ADMIN: 0, Rolle.TRAINER: 1, Rolle.SPIELER: 2}
_LEITUNG = (Rolle.ADMIN, Rolle.TRAINER)


class Bereich(StrEnum):
    EINZELN = "einzeln"
    FOLGENDE = "folgende"


# --- Zeit ---------------------------------------------------------------------


def utc(zeitpunkt: datetime) -> datetime:
    """Immer in UTC speichern – SQLite kennt keine Zeitzonen."""
    return als_utc(zeitpunkt).astimezone(UTC)


def _zone(request: Request) -> ZoneInfo:
    return ZoneInfo(request.app.state.einstellungen.zeitzone)


def _lokal(zeitpunkt: datetime, zone: ZoneInfo) -> datetime:
    return als_utc(zeitpunkt).astimezone(zone).replace(tzinfo=None)


def _aus_lokal(naiv: datetime, zone: ZoneInfo) -> datetime:
    return naiv.replace(tzinfo=zone).astimezone(UTC)


def serie_berechnen(
    beginn: datetime, ende: datetime | None, wiederholung: Wiederholung, zone: ZoneInfo
) -> list[tuple[datetime, datetime | None]]:
    """Alle Termine einer wöchentlichen Serie – die Uhrzeit bleibt lokal gleich (Sommer-/Winterzeit)."""
    start = _lokal(beginn, zone)
    dauer = (ende - beginn) if ende else None
    tage = wiederholung.wochentage or [start.weekday()]
    letzter = min(wiederholung.bis, start.date() + timedelta(days=MAX_SERIE_TAGE))
    termine = []
    tag = start.date()
    while tag <= letzter:
        if tag.weekday() in tage:
            b = _aus_lokal(datetime.combine(tag, start.time()), zone)
            termine.append((b, b + dauer if dauer else None))
            if len(termine) > MAX_SERIE:
                raise HTTPException(
                    status.HTTP_422_UNPROCESSABLE_ENTITY, f"Eine Serie darf höchstens {MAX_SERIE} Termine haben"
                )
        tag += timedelta(days=1)
    return termine


def antwort_bis(termin: Termin) -> datetime:
    return utc(termin.beginn) - timedelta(hours=termin.frist_stunden or 0)


def saison_beginn(heute: date) -> date:
    """Fußballsaison beginnt am 1. Juli."""
    return date(heute.year if heute.month >= 7 else heute.year - 1, 7, 1)


# --- Auswertung -----------------------------------------------------------------


def _mitglieder(team: Team) -> list[Mitgliedschaft]:
    return sorted(
        team.mitgliedschaften,
        key=lambda m: (_ROLLEN_REIHENFOLGE[Rolle(m.rolle)], m.nutzer.name.lower()),
    )


def _antworten(db: Session, termin_ids: list[int]) -> dict[tuple[int, int], Antwort]:
    if not termin_ids:
        return {}
    zeilen = db.scalars(select(Antwort).where(Antwort.termin_id.in_(termin_ids)))
    return {(a.termin_id, a.nutzer_id): a for a in zeilen}


def _wirksam(termin: Termin, nutzer_id: int, antworten: dict) -> tuple[Status, Antwort | None]:
    antwort = antworten.get((termin.id, nutzer_id))
    if antwort is not None:
        return Status(antwort.status), antwort
    return Status(termin.team.standard(termin.art)), None


def _ausgabe(
    termin: Termin, ich: Mitgliedschaft, mitglieder: list[Mitgliedschaft], antworten: dict, detail: bool = False
) -> TerminAusgabe | TerminDetail:
    leitung = ich.rolle in _LEITUNG
    zaehler = Zaehler()
    teilnehmer = []
    eigene = EigeneAntwort(status=Status.OFFEN, automatisch=True)
    for m in mitglieder:
        st, antwort = _wirksam(termin, m.nutzer_id, antworten)
        setattr(zaehler, st.value, getattr(zaehler, st.value) + 1)
        grund = antwort.grund if antwort else None
        if m.nutzer_id == ich.nutzer_id:
            eigene = EigeneAntwort(status=st, grund=grund, automatisch=antwort is None)
        if detail:
            teilnehmer.append(
                Teilnehmer(
                    nutzer_id=m.nutzer_id,
                    name=m.nutzer.name,
                    rolle=Rolle(m.rolle),
                    status=st,
                    grund=grund if leitung or m.nutzer_id == ich.nutzer_id else None,
                    automatisch=antwort is None,
                    geaendert_am=utc(antwort.geaendert_am) if antwort else None,
                )
            )
    werte = dict(
        id=termin.id,
        serie_id=termin.serie_id,
        art=Terminart(termin.art),
        titel=termin.titel,
        beginn=utc(termin.beginn),
        ende=utc(termin.ende) if termin.ende else None,
        treffen_vorher_min=termin.treffen_vorher_min,
        ort=termin.ort,
        notiz=termin.notiz,
        gegner=termin.gegner,
        heimspiel=termin.heimspiel,
        frist_stunden=termin.frist_stunden,
        abgesagt=termin.abgesagt,
        absage_grund=termin.absage_grund,
        antwort_bis=antwort_bis(termin),
        eigene=eigene,
        zaehler=zaehler,
    )
    return TerminDetail(**werte, teilnehmer=teilnehmer) if detail else TerminAusgabe(**werte)


def _termin_holen(db: Session, team_id: int, termin_id: int) -> Termin:
    termin = db.get(Termin, termin_id)
    if termin is None or termin.team_id != team_id:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Termin nicht gefunden")
    return termin


def _detail(db: Session, termin: Termin, ich: Mitgliedschaft) -> TerminDetail:
    return _ausgabe(termin, ich, _mitglieder(termin.team), _antworten(db, [termin.id]), detail=True)


def _betroffene(db: Session, termin: Termin, bereich: Bereich) -> list[Termin]:
    if bereich == Bereich.EINZELN or termin.serie_id is None:
        return [termin]
    return list(
        db.scalars(
            select(Termin)
            .where(
                Termin.team_id == termin.team_id,
                Termin.serie_id == termin.serie_id,
                Termin.beginn >= utc(termin.beginn),
            )
            .order_by(Termin.beginn)
        )
    )


# --- Termine --------------------------------------------------------------------


@router.get("/termine")
def termine(
    team_id: int,
    nutzer: AktNutzer,
    db: Db,
    vergangene: bool = False,
    von: datetime | None = None,
    bis: datetime | None = None,
    limit: int = Query(default=100, ge=1, le=500),
) -> list[TerminAusgabe]:
    """Kommende Termine (ab 3 Stunden zurück, aufsteigend) oder vergangene (absteigend)."""
    ich = mitgliedschaft_holen(db, nutzer, team_id)
    if vergangene:
        bis = utc(bis) if bis else jetzt()
        von = utc(von) if von else bis - timedelta(days=365)
        reihenfolge = Termin.beginn.desc()
        bedingung = (Termin.beginn >= von, Termin.beginn < bis)
    else:
        von = utc(von) if von else jetzt() - timedelta(hours=3)
        bis = utc(bis) if bis else von + timedelta(days=MAX_SERIE_TAGE)
        reihenfolge = Termin.beginn.asc()
        bedingung = (Termin.beginn >= von, Termin.beginn <= bis)
    liste = list(
        db.scalars(select(Termin).where(Termin.team_id == team_id, *bedingung).order_by(reihenfolge).limit(limit))
    )
    mitglieder = _mitglieder(ich.team)
    antworten = _antworten(db, [t.id for t in liste])
    return [_ausgabe(t, ich, mitglieder, antworten) for t in liste]


@router.post("/termine", status_code=status.HTTP_201_CREATED)
def termin_anlegen(
    team_id: int, daten: TerminNeu, nutzer: AktNutzer, db: Db, request: Request
) -> list[TerminAusgabe]:
    """Legt einen Termin an – mit `wiederholung` eine ganze Serie."""
    ich = mitgliedschaft_holen(db, nutzer, team_id)
    rolle_verlangen(ich, *_LEITUNG)
    if daten.wiederholung:
        zeiten = serie_berechnen(daten.beginn, daten.ende, daten.wiederholung, _zone(request))
        if not zeiten:
            raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, "Die Serie enthält keinen Termin")
        serie_id = uuid.uuid4().hex
    else:
        zeiten = [(daten.beginn, daten.ende)]
        serie_id = None
    felder = daten.model_dump(exclude={"beginn", "ende", "wiederholung"})
    neu = [
        Termin(
            team_id=team_id,
            serie_id=serie_id,
            beginn=utc(b),
            ende=utc(e) if e else None,
            erstellt_von_id=nutzer.id,
            **felder,
        )
        for b, e in zeiten
    ]
    db.add_all(neu)
    db.commit()
    mitglieder = _mitglieder(ich.team)
    return [_ausgabe(t, ich, mitglieder, {}) for t in neu]


@router.get("/termine/{termin_id}")
def termin_lesen(team_id: int, termin_id: int, nutzer: AktNutzer, db: Db) -> TerminDetail:
    ich = mitgliedschaft_holen(db, nutzer, team_id)
    return _detail(db, _termin_holen(db, team_id, termin_id), ich)


@router.patch("/termine/{termin_id}")
def termin_aendern(
    team_id: int,
    termin_id: int,
    daten: TerminAendern,
    nutzer: AktNutzer,
    db: Db,
    request: Request,
    bereich: Bereich = Bereich.EINZELN,
) -> TerminDetail:
    """Ändert einen Termin oder – mit `bereich=folgende` – ihn und alle späteren der Serie.

    Eine geänderte Uhrzeit wird als Verschiebung auf alle betroffenen Termine übertragen.
    """
    ich = mitgliedschaft_holen(db, nutzer, team_id)
    rolle_verlangen(ich, *_LEITUNG)
    termin = _termin_holen(db, team_id, termin_id)
    zone = _zone(request)
    felder = daten.model_dump(exclude_unset=True)
    neuer_beginn = felder.pop("beginn", None)
    hat_ende = "ende" in felder
    neues_ende = felder.pop("ende", None)
    if felder.get("abgesagt") is False:
        felder["absage_grund"] = None

    verschiebung = (_lokal(neuer_beginn, zone) - _lokal(termin.beginn, zone)) if neuer_beginn else None
    basis_beginn = neuer_beginn or termin.beginn
    dauer = (neues_ende - basis_beginn) if hat_ende and neues_ende is not None else None

    for t in _betroffene(db, termin, bereich):
        for name, wert in felder.items():
            setattr(t, name, wert)
        if verschiebung:
            t.beginn = _aus_lokal(_lokal(t.beginn, zone) + verschiebung, zone)
            if t.ende and not hat_ende:
                t.ende = _aus_lokal(_lokal(t.ende, zone) + verschiebung, zone)
        if hat_ende:
            t.ende = utc(t.beginn) + dauer if dauer is not None else None
        if t.ende is not None and utc(t.ende) <= utc(t.beginn):
            raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, "Ende muss nach dem Beginn liegen")
    db.commit()
    return _detail(db, termin, ich)


@router.delete("/termine/{termin_id}", status_code=status.HTTP_204_NO_CONTENT)
def termin_loeschen(
    team_id: int, termin_id: int, nutzer: AktNutzer, db: Db, bereich: Bereich = Bereich.EINZELN
) -> Response:
    rolle_verlangen(mitgliedschaft_holen(db, nutzer, team_id), *_LEITUNG)
    for t in _betroffene(db, _termin_holen(db, team_id, termin_id), bereich):
        db.delete(t)
    db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)


# --- Antworten ------------------------------------------------------------------


@router.put("/termine/{termin_id}/antwort")
def antworten(team_id: int, termin_id: int, daten: AntwortSetzen, nutzer: AktNutzer, db: Db) -> TerminDetail:
    """Zu-/Absage. `offen` nimmt die eigene Antwort zurück. Trainer dürfen für andere eintragen
    und sind nicht an Frist oder Absage gebunden."""
    ich = mitgliedschaft_holen(db, nutzer, team_id)
    termin = _termin_holen(db, team_id, termin_id)
    leitung = ich.rolle in _LEITUNG
    ziel_id = daten.nutzer_id or nutzer.id
    if ziel_id != nutzer.id:
        rolle_verlangen(ich, *_LEITUNG)
        if db.get(Nutzer, ziel_id) is None or not any(m.nutzer_id == ziel_id for m in ich.team.mitgliedschaften):
            raise HTTPException(status.HTTP_404_NOT_FOUND, "Mitglied nicht gefunden")
    if not leitung:
        if termin.abgesagt:
            raise HTTPException(status.HTTP_409_CONFLICT, "Der Termin ist abgesagt")
        if jetzt() > antwort_bis(termin):
            raise HTTPException(status.HTTP_409_CONFLICT, "Antwortfrist abgelaufen – bitte beim Trainer melden")

    antwort = db.scalar(select(Antwort).where(Antwort.termin_id == termin.id, Antwort.nutzer_id == ziel_id))
    if daten.status == Status.OFFEN:
        if antwort is not None:
            db.delete(antwort)
    elif antwort is None:
        db.add(
            Antwort(
                termin_id=termin.id,
                nutzer_id=ziel_id,
                status=daten.status,
                grund=daten.grund,
                eingetragen_von_id=nutzer.id,
            )
        )
    else:
        antwort.status = daten.status
        antwort.grund = daten.grund
        antwort.eingetragen_von_id = nutzer.id
        antwort.geaendert_am = jetzt()
    db.commit()
    db.expire(termin, ["antworten"])
    return _detail(db, termin, ich)


# --- Team-Einstellungen und Statistik ------------------------------------------------


@router.get("/standardantworten")
def standardantworten(team_id: int, nutzer: AktNutzer, db: Db) -> Standardantworten:
    team = mitgliedschaft_holen(db, nutzer, team_id).team
    return Standardantworten(training=team.standard_training, spiel=team.standard_spiel, event=team.standard_event)


@router.put("/standardantworten")
def standardantworten_setzen(team_id: int, daten: Standardantworten, nutzer: AktNutzer, db: Db) -> Standardantworten:
    ich = mitgliedschaft_holen(db, nutzer, team_id)
    rolle_verlangen(ich, Rolle.ADMIN)
    ich.team.standard_training = daten.training
    ich.team.standard_spiel = daten.spiel
    ich.team.standard_event = daten.event
    db.commit()
    return daten


@router.get("/statistik")
def statistik(
    team_id: int,
    nutzer: AktNutzer,
    db: Db,
    request: Request,
    art: Terminart = Terminart.TRAINING,
    von: datetime | None = None,
    bis: datetime | None = None,
) -> Statistik:
    """Beteiligung vergangener, nicht abgesagter Termine – Standard: seit Saisonbeginn (1. Juli).
    Gezählt werden für jedes Mitglied nur Termine ab seinem Beitritt."""
    ich = mitgliedschaft_holen(db, nutzer, team_id)
    rolle_verlangen(ich, *_LEITUNG)
    zone = _zone(request)
    bis = min(utc(bis), jetzt()) if bis else jetzt()
    von = utc(von) if von else _aus_lokal(datetime.combine(saison_beginn(bis.astimezone(zone).date()), time()), zone)
    liste = list(
        db.scalars(
            select(Termin).where(
                Termin.team_id == team_id,
                Termin.art == art,
                Termin.abgesagt.is_(False),
                Termin.beginn >= von,
                Termin.beginn < bis,
            )
        )
    )
    antworten = _antworten(db, [t.id for t in liste])
    zeilen = []
    for m in _mitglieder(ich.team):
        z = Zaehler()
        anzahl = 0
        for t in liste:
            if utc(t.beginn) < utc(m.beigetreten_am):
                continue
            anzahl += 1
            st, _ = _wirksam(t, m.nutzer_id, antworten)
            setattr(z, st.value, getattr(z, st.value) + 1)
        zeilen.append(
            StatistikZeile(
                nutzer_id=m.nutzer_id,
                name=m.nutzer.name,
                rolle=Rolle(m.rolle),
                termine=anzahl,
                quote=round(z.zugesagt / anzahl, 3) if anzahl else 0.0,
                **z.model_dump(),
            )
        )
    zeilen.sort(key=lambda s: (-s.quote, s.name.lower()))
    return Statistik(art=art, von=von, bis=bis, zeilen=zeilen)
