"""Fachlogik, die mehrere Routen gemeinsam nutzen."""

from __future__ import annotations

from datetime import timedelta

from fastapi import HTTPException, status
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from .datenbank import als_utc, jetzt
from .einstellungen import Einstellungen
from .modelle import Einladung, Mitgliedschaft, Nutzer, Rolle, Token
from .schemas import TokenAntwort
from .sicherheit import code_normalisieren, token_erzeugen, token_hash


def token_ausstellen(
    db: Session, nutzer: Nutzer, geraet: str | None, einstellungen: Einstellungen
) -> TokenAntwort:
    klartext = token_erzeugen()
    gueltig_bis = jetzt() + timedelta(days=einstellungen.token_tage)
    db.add(Token(nutzer=nutzer, token_hash=token_hash(klartext), geraet=geraet, gueltig_bis=gueltig_bis))
    return TokenAntwort(token=klartext, gueltig_bis=gueltig_bis)


def einladung_pruefen(db: Session, code: str) -> Einladung:
    einladung = db.scalar(
        select(Einladung).where(Einladung.code == code_normalisieren(code)).with_for_update()
    )
    if (
        einladung is None
        or als_utc(einladung.gueltig_bis) <= jetzt()
        or (einladung.max_nutzungen is not None and einladung.nutzungen >= einladung.max_nutzungen)
    ):
        raise HTTPException(status.HTTP_400_BAD_REQUEST, "Einladungscode ungültig oder abgelaufen")
    return einladung


def beitreten(db: Session, einladung: Einladung, nutzer: Nutzer) -> Mitgliedschaft:
    if nutzer.id is not None and db.scalar(
        select(Mitgliedschaft).where(
            Mitgliedschaft.team_id == einladung.team_id, Mitgliedschaft.nutzer_id == nutzer.id
        )
    ):
        raise HTTPException(status.HTTP_409_CONFLICT, "Du bist schon Mitglied in diesem Team")
    mitgliedschaft = Mitgliedschaft(nutzer=nutzer, team=einladung.team, rolle=einladung.rolle)
    db.add(mitgliedschaft)
    einladung.nutzungen += 1
    return mitgliedschaft


def letzten_admin_schuetzen(db: Session, ziel: Mitgliedschaft) -> None:
    """Verhindert, dass ein Team ohne Admin zurückbleibt."""
    if ziel.rolle != Rolle.ADMIN:
        return
    admins = db.scalar(
        select(func.count())
        .select_from(Mitgliedschaft)
        .where(Mitgliedschaft.team_id == ziel.team_id, Mitgliedschaft.rolle == Rolle.ADMIN)
    )
    if admins <= 1:
        raise HTTPException(status.HTTP_409_CONFLICT, "Das Team braucht mindestens einen Admin")
