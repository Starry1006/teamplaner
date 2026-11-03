"""Ein- und Ausgabeformate für Termine und Antworten."""

from __future__ import annotations

from datetime import date, datetime
from typing import Literal

from pydantic import AwareDatetime, BaseModel, Field, field_validator, model_validator

from .modelle import Rolle, Status, Terminart

Standard = Literal["offen", "zugesagt"]


def _leer_zu_none(wert: str | None) -> str | None:
    if wert is None:
        return None
    wert = wert.strip()
    return wert or None


class Wiederholung(BaseModel):
    """Wöchentliche Serie. Wochentage: 0 = Montag … 6 = Sonntag; leer = Wochentag des ersten Termins."""

    wochentage: list[int] = Field(default_factory=list, max_length=7)
    bis: date

    @field_validator("wochentage")
    @classmethod
    def _tage(cls, tage: list[int]) -> list[int]:
        if any(t < 0 or t > 6 for t in tage):
            raise ValueError("Wochentage 0 (Montag) bis 6 (Sonntag)")
        return sorted(set(tage))


class _Texte(BaseModel):
    titel: str | None = Field(default=None, max_length=100)
    ort: str | None = Field(default=None, max_length=200)
    notiz: str | None = Field(default=None, max_length=2000)
    gegner: str | None = Field(default=None, max_length=100)
    absage_grund: str | None = Field(default=None, max_length=200)

    _bereinigen = field_validator("titel", "ort", "notiz", "gegner", "absage_grund")(_leer_zu_none)


class TerminNeu(_Texte):
    art: Terminart
    beginn: AwareDatetime
    ende: AwareDatetime | None = None
    treffen_vorher_min: int | None = Field(default=None, ge=0, le=600)
    heimspiel: bool | None = None
    frist_stunden: int | None = Field(default=None, ge=0, le=336)
    wiederholung: Wiederholung | None = None

    @model_validator(mode="after")
    def _zeiten(self) -> TerminNeu:
        if self.ende is not None and self.ende <= self.beginn:
            raise ValueError("Ende muss nach dem Beginn liegen")
        if self.wiederholung and self.wiederholung.bis < self.beginn.date():
            raise ValueError("Serienende liegt vor dem ersten Termin")
        return self


class TerminAendern(_Texte):
    """Nur die mitgeschickten Felder werden geändert."""

    art: Terminart | None = None
    beginn: AwareDatetime | None = None
    ende: AwareDatetime | None = None
    treffen_vorher_min: int | None = Field(default=None, ge=0, le=600)
    heimspiel: bool | None = None
    frist_stunden: int | None = Field(default=None, ge=0, le=336)
    abgesagt: bool | None = None


class Zaehler(BaseModel):
    zugesagt: int = 0
    abgesagt: int = 0
    vielleicht: int = 0
    offen: int = 0


class EigeneAntwort(BaseModel):
    status: Status
    grund: str | None = None
    # True = Standardantwort des Teams, nicht selbst gewählt.
    automatisch: bool = False


class TerminAusgabe(BaseModel):
    id: int
    serie_id: str | None
    art: Terminart
    titel: str | None
    beginn: datetime
    ende: datetime | None
    treffen_vorher_min: int | None
    ort: str | None
    notiz: str | None
    gegner: str | None
    heimspiel: bool | None
    frist_stunden: int | None
    abgesagt: bool
    absage_grund: str | None
    antwort_bis: datetime
    eigene: EigeneAntwort
    zaehler: Zaehler


class Teilnehmer(BaseModel):
    nutzer_id: int
    name: str
    rolle: Rolle
    status: Status
    # Nur für Admins/Trainer und die Person selbst sichtbar.
    grund: str | None = None
    automatisch: bool = False
    geaendert_am: datetime | None = None


class TerminDetail(TerminAusgabe):
    teilnehmer: list[Teilnehmer]


class AntwortSetzen(BaseModel):
    status: Status
    grund: str | None = Field(default=None, max_length=200)
    # Nur Admins/Trainer: Antwort für ein anderes Mitglied eintragen.
    nutzer_id: int | None = None

    _bereinigen = field_validator("grund")(_leer_zu_none)


class Standardantworten(BaseModel):
    training: Standard
    spiel: Standard
    event: Standard


class StatistikZeile(BaseModel):
    nutzer_id: int
    name: str
    rolle: Rolle
    termine: int
    zugesagt: int
    abgesagt: int
    vielleicht: int
    offen: int
    quote: float


class Statistik(BaseModel):
    art: Terminart
    von: datetime
    bis: datetime
    zeilen: list[StatistikZeile]
