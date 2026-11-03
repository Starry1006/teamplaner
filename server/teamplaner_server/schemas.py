"""Ein- und Ausgabeformate der REST-Schnittstelle."""

from __future__ import annotations

from datetime import datetime

from pydantic import BaseModel, ConfigDict, EmailStr, Field, field_validator

from .modelle import Rolle

PASSWORT_MIN = 8


def _name_bereinigen(name: str) -> str:
    name = " ".join(name.split())
    if not name:
        raise ValueError("Name darf nicht leer sein")
    return name


class Registrierung(BaseModel):
    code: str = Field(min_length=4, max_length=32)
    name: str = Field(max_length=100)
    email: EmailStr
    passwort: str = Field(min_length=PASSWORT_MIN, max_length=200)
    geraet: str | None = Field(default=None, max_length=100)

    _name = field_validator("name")(_name_bereinigen)


class Anmeldung(BaseModel):
    email: EmailStr
    passwort: str = Field(max_length=200)
    geraet: str | None = Field(default=None, max_length=100)


class TokenAntwort(BaseModel):
    token: str
    gueltig_bis: datetime


class TeamKurz(BaseModel):
    id: int
    name: str
    rolle: Rolle


class Ich(BaseModel):
    id: int
    name: str
    email: str
    teams: list[TeamKurz]


class ProfilAendern(BaseModel):
    name: str = Field(max_length=100)

    _name = field_validator("name")(_name_bereinigen)


class PasswortAendern(BaseModel):
    alt: str = Field(max_length=200)
    neu: str = Field(min_length=PASSWORT_MIN, max_length=200)


class TeamNeu(BaseModel):
    name: str = Field(max_length=100)

    _name = field_validator("name")(_name_bereinigen)


class TeamAusgabe(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    name: str


class Mitglied(BaseModel):
    nutzer_id: int
    name: str
    # Nur für Admins und Trainer sichtbar.
    email: str | None = None
    rolle: Rolle
    beigetreten_am: datetime


class RolleAendern(BaseModel):
    rolle: Rolle


class EinladungNeu(BaseModel):
    rolle: Rolle = Rolle.SPIELER
    gueltig_tage: int | None = Field(default=None, ge=1, le=365)
    max_nutzungen: int | None = Field(default=None, ge=1, le=1000)


class EinladungAusgabe(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    code: str
    rolle: Rolle
    gueltig_bis: datetime
    max_nutzungen: int | None
    nutzungen: int


class CodeEinloesen(BaseModel):
    code: str = Field(min_length=4, max_length=32)
