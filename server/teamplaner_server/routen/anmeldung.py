"""Registrieren (nur mit Einladungscode), Anmelden, Abmelden."""

from __future__ import annotations

from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, Request, Response, status
from sqlalchemy import select

from ..abhaengigkeiten import Db, aktuelles_token
from ..dienste import beitreten, einladung_pruefen, token_ausstellen
from ..modelle import Nutzer, Token
from ..schemas import Anmeldung, Registrierung, TokenAntwort
from ..sicherheit import BLINDER_HASH, passwort_hashen, passwort_pruefen

router = APIRouter(prefix="/auth", tags=["Anmeldung"])


@router.post("/registrieren", status_code=status.HTTP_201_CREATED)
def registrieren(daten: Registrierung, db: Db, request: Request) -> TokenAntwort:
    email = daten.email.lower()
    if db.scalar(select(Nutzer).where(Nutzer.email == email)):
        raise HTTPException(
            status.HTTP_409_CONFLICT,
            "Diese E-Mail ist schon registriert – bitte anmelden und den Code dort einlösen",
        )
    einladung = einladung_pruefen(db, daten.code)
    nutzer = Nutzer(name=daten.name, email=email, passwort_hash=passwort_hashen(daten.passwort))
    db.add(nutzer)
    beitreten(db, einladung, nutzer)
    antwort = token_ausstellen(db, nutzer, daten.geraet, request.app.state.einstellungen)
    db.commit()
    return antwort


@router.post("/anmelden")
def anmelden(daten: Anmeldung, db: Db, request: Request) -> TokenAntwort:
    nutzer = db.scalar(select(Nutzer).where(Nutzer.email == daten.email.lower()))
    if nutzer is None:
        passwort_pruefen(BLINDER_HASH, daten.passwort)
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "E-Mail oder Passwort falsch")
    if not passwort_pruefen(nutzer.passwort_hash, daten.passwort):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "E-Mail oder Passwort falsch")
    antwort = token_ausstellen(db, nutzer, daten.geraet, request.app.state.einstellungen)
    db.commit()
    return antwort


@router.post("/abmelden", status_code=status.HTTP_204_NO_CONTENT)
def abmelden(db: Db, token: Annotated[Token, Depends(aktuelles_token)]) -> Response:
    db.delete(token)
    db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)
