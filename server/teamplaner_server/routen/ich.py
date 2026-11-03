"""Eigenes Profil und Einlösen weiterer Einladungscodes."""

from __future__ import annotations

from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, Response, status
from sqlalchemy import delete

from ..abhaengigkeiten import AktNutzer, Db, aktuelles_token
from ..dienste import beitreten, einladung_pruefen
from ..modelle import Nutzer, Rolle, Token
from ..schemas import CodeEinloesen, Ich, PasswortAendern, ProfilAendern, TeamKurz
from ..sicherheit import passwort_hashen, passwort_pruefen

router = APIRouter(tags=["Profil"])


def _ich(nutzer: Nutzer) -> Ich:
    teams = sorted(
        (TeamKurz(id=m.team.id, name=m.team.name, rolle=Rolle(m.rolle)) for m in nutzer.mitgliedschaften),
        key=lambda t: t.name.lower(),
    )
    return Ich(id=nutzer.id, name=nutzer.name, email=nutzer.email, teams=teams)


@router.get("/ich")
def ich(nutzer: AktNutzer) -> Ich:
    return _ich(nutzer)


@router.patch("/ich")
def profil_aendern(daten: ProfilAendern, nutzer: AktNutzer, db: Db) -> Ich:
    nutzer.name = daten.name
    db.commit()
    return _ich(nutzer)


@router.put("/ich/passwort", status_code=status.HTTP_204_NO_CONTENT)
def passwort_aendern(
    daten: PasswortAendern, db: Db, token: Annotated[Token, Depends(aktuelles_token)]
) -> Response:
    nutzer = token.nutzer
    if not passwort_pruefen(nutzer.passwort_hash, daten.alt):
        raise HTTPException(status.HTTP_400_BAD_REQUEST, "Bisheriges Passwort ist falsch")
    nutzer.passwort_hash = passwort_hashen(daten.neu)
    # Alle anderen Geräte abmelden, dieses bleibt angemeldet.
    db.execute(delete(Token).where(Token.nutzer_id == nutzer.id, Token.id != token.id))
    db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.post("/einladungen/einloesen")
def code_einloesen(daten: CodeEinloesen, nutzer: AktNutzer, db: Db) -> TeamKurz:
    einladung = einladung_pruefen(db, daten.code)
    m = beitreten(db, einladung, nutzer)
    db.commit()
    return TeamKurz(id=m.team.id, name=m.team.name, rolle=Rolle(m.rolle))
