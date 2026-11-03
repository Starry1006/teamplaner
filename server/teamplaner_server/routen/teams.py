"""Teams, Mitglieder, Rollen und Einladungen."""

from __future__ import annotations

from datetime import timedelta

from fastapi import APIRouter, HTTPException, Request, Response, status
from sqlalchemy import or_, select

from ..abhaengigkeiten import AktNutzer, Db, mitgliedschaft_holen, rolle_verlangen
from ..datenbank import jetzt
from ..dienste import letzten_admin_schuetzen
from ..modelle import Einladung, Mitgliedschaft, Rolle, Team
from ..schemas import (
    EinladungAusgabe,
    EinladungNeu,
    Mitglied,
    RolleAendern,
    TeamAusgabe,
    TeamKurz,
    TeamNeu,
)
from ..sicherheit import code_erzeugen

router = APIRouter(prefix="/teams", tags=["Teams"])

_ROLLEN_REIHENFOLGE = {Rolle.ADMIN: 0, Rolle.TRAINER: 1, Rolle.SPIELER: 2}


@router.post("", status_code=status.HTTP_201_CREATED)
def team_anlegen(daten: TeamNeu, nutzer: AktNutzer, db: Db) -> TeamKurz:
    team = Team(name=daten.name)
    db.add(Mitgliedschaft(nutzer=nutzer, team=team, rolle=Rolle.ADMIN))
    db.commit()
    return TeamKurz(id=team.id, name=team.name, rolle=Rolle.ADMIN)


@router.get("/{team_id}")
def team_lesen(team_id: int, nutzer: AktNutzer, db: Db) -> TeamAusgabe:
    return TeamAusgabe.model_validate(mitgliedschaft_holen(db, nutzer, team_id).team)


@router.patch("/{team_id}")
def team_umbenennen(team_id: int, daten: TeamNeu, nutzer: AktNutzer, db: Db) -> TeamAusgabe:
    m = mitgliedschaft_holen(db, nutzer, team_id)
    rolle_verlangen(m, Rolle.ADMIN)
    m.team.name = daten.name
    db.commit()
    return TeamAusgabe.model_validate(m.team)


# --- Mitglieder ---------------------------------------------------------------


@router.get("/{team_id}/mitglieder")
def mitglieder(team_id: int, nutzer: AktNutzer, db: Db) -> list[Mitglied]:
    eigene = mitgliedschaft_holen(db, nutzer, team_id)
    email_sichtbar = eigene.rolle in (Rolle.ADMIN, Rolle.TRAINER)
    alle = sorted(
        eigene.team.mitgliedschaften,
        key=lambda m: (_ROLLEN_REIHENFOLGE[Rolle(m.rolle)], m.nutzer.name.lower()),
    )
    return [
        Mitglied(
            nutzer_id=m.nutzer_id,
            name=m.nutzer.name,
            email=m.nutzer.email if email_sichtbar or m.nutzer_id == nutzer.id else None,
            rolle=Rolle(m.rolle),
            beigetreten_am=m.beigetreten_am,
        )
        for m in alle
    ]


def _ziel_holen(db: Db, team_id: int, nutzer_id: int) -> Mitgliedschaft:
    ziel = db.scalar(
        select(Mitgliedschaft).where(
            Mitgliedschaft.team_id == team_id, Mitgliedschaft.nutzer_id == nutzer_id
        )
    )
    if ziel is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Mitglied nicht gefunden")
    return ziel


@router.patch("/{team_id}/mitglieder/{nutzer_id}", status_code=status.HTTP_204_NO_CONTENT)
def rolle_aendern(
    team_id: int, nutzer_id: int, daten: RolleAendern, nutzer: AktNutzer, db: Db
) -> Response:
    rolle_verlangen(mitgliedschaft_holen(db, nutzer, team_id), Rolle.ADMIN)
    ziel = _ziel_holen(db, team_id, nutzer_id)
    if daten.rolle != Rolle.ADMIN:
        letzten_admin_schuetzen(db, ziel)
    ziel.rolle = daten.rolle
    db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.delete("/{team_id}/mitglieder/{nutzer_id}", status_code=status.HTTP_204_NO_CONTENT)
def mitglied_entfernen(team_id: int, nutzer_id: int, nutzer: AktNutzer, db: Db) -> Response:
    """Admins entfernen Mitglieder; jedes Mitglied kann selbst austreten."""
    eigene = mitgliedschaft_holen(db, nutzer, team_id)
    if nutzer_id != nutzer.id:
        rolle_verlangen(eigene, Rolle.ADMIN)
    ziel = _ziel_holen(db, team_id, nutzer_id)
    letzten_admin_schuetzen(db, ziel)
    db.delete(ziel)
    db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)


# --- Einladungen --------------------------------------------------------------


@router.post("/{team_id}/einladungen", status_code=status.HTTP_201_CREATED)
def einladung_erstellen(
    team_id: int, daten: EinladungNeu, nutzer: AktNutzer, db: Db, request: Request
) -> EinladungAusgabe:
    m = mitgliedschaft_holen(db, nutzer, team_id)
    rolle_verlangen(m, Rolle.ADMIN, Rolle.TRAINER)
    if m.rolle == Rolle.TRAINER and daten.rolle != Rolle.SPIELER:
        raise HTTPException(status.HTTP_403_FORBIDDEN, "Trainer können nur Spieler einladen")
    tage = daten.gueltig_tage or request.app.state.einstellungen.einladung_tage
    code = code_erzeugen()
    while db.scalar(select(Einladung.id).where(Einladung.code == code)):
        code = code_erzeugen()
    einladung = Einladung(
        team_id=team_id,
        code=code,
        rolle=daten.rolle,
        erstellt_von_id=nutzer.id,
        gueltig_bis=jetzt() + timedelta(days=tage),
        max_nutzungen=daten.max_nutzungen,
    )
    db.add(einladung)
    db.commit()
    return EinladungAusgabe.model_validate(einladung)


@router.get("/{team_id}/einladungen")
def einladungen(team_id: int, nutzer: AktNutzer, db: Db) -> list[EinladungAusgabe]:
    """Nur noch nutzbare Einladungen."""
    rolle_verlangen(mitgliedschaft_holen(db, nutzer, team_id), Rolle.ADMIN, Rolle.TRAINER)
    gueltige = db.scalars(
        select(Einladung)
        .where(
            Einladung.team_id == team_id,
            Einladung.gueltig_bis > jetzt(),
            or_(Einladung.max_nutzungen.is_(None), Einladung.nutzungen < Einladung.max_nutzungen),
        )
        .order_by(Einladung.erstellt_am.desc())
    )
    return [EinladungAusgabe.model_validate(e) for e in gueltige]


@router.delete("/{team_id}/einladungen/{einladung_id}", status_code=status.HTTP_204_NO_CONTENT)
def einladung_loeschen(team_id: int, einladung_id: int, nutzer: AktNutzer, db: Db) -> Response:
    rolle_verlangen(mitgliedschaft_holen(db, nutzer, team_id), Rolle.ADMIN, Rolle.TRAINER)
    einladung = db.get(Einladung, einladung_id)
    if einladung is None or einladung.team_id != team_id:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Einladung nicht gefunden")
    db.delete(einladung)
    db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)
