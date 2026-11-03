"""Gemeinsame FastAPI-Abhängigkeiten: Datenbank-Sitzung, angemeldeter Nutzer, Rechte."""

from __future__ import annotations

from collections.abc import Iterator
from typing import Annotated

from fastapi import Depends, HTTPException, Request, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy import select
from sqlalchemy.orm import Session

from .datenbank import als_utc, jetzt
from .modelle import Mitgliedschaft, Nutzer, Rolle, Token
from .sicherheit import token_hash

_bearer = HTTPBearer(auto_error=False)


def datenbank(request: Request) -> Iterator[Session]:
    with request.app.state.sitzungen() as sitzung:
        yield sitzung


Db = Annotated[Session, Depends(datenbank)]


def aktuelles_token(
    db: Db,
    zugang: Annotated[HTTPAuthorizationCredentials | None, Depends(_bearer)],
) -> Token:
    if zugang is None:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Nicht angemeldet")
    token = db.scalar(select(Token).where(Token.token_hash == token_hash(zugang.credentials)))
    if token is None or als_utc(token.gueltig_bis) <= jetzt():
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Anmeldung abgelaufen")
    return token


def aktueller_nutzer(token: Annotated[Token, Depends(aktuelles_token)]) -> Nutzer:
    return token.nutzer


AktNutzer = Annotated[Nutzer, Depends(aktueller_nutzer)]


def mitgliedschaft_holen(db: Session, nutzer: Nutzer, team_id: int) -> Mitgliedschaft:
    """Mitgliedschaft im Team – sonst 404, damit fremde Teams unsichtbar bleiben."""
    m = db.scalar(
        select(Mitgliedschaft).where(
            Mitgliedschaft.team_id == team_id, Mitgliedschaft.nutzer_id == nutzer.id
        )
    )
    if m is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Team nicht gefunden")
    return m


def rolle_verlangen(m: Mitgliedschaft, *erlaubt: Rolle) -> None:
    if m.rolle not in erlaubt:
        raise HTTPException(status.HTTP_403_FORBIDDEN, "Dafür fehlen die Rechte")
