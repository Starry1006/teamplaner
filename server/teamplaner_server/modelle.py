"""Datenmodell."""

from __future__ import annotations

from datetime import datetime
from enum import StrEnum

from sqlalchemy import Boolean, DateTime, ForeignKey, Integer, String, Text, UniqueConstraint, false
from sqlalchemy.orm import Mapped, mapped_column, relationship

from .datenbank import Basis, jetzt


class Rolle(StrEnum):
    ADMIN = "admin"
    TRAINER = "trainer"
    SPIELER = "spieler"


class Nutzer(Basis):
    __tablename__ = "nutzer"

    id: Mapped[int] = mapped_column(primary_key=True)
    name: Mapped[str] = mapped_column(String(100))
    email: Mapped[str] = mapped_column(String(254), unique=True)
    passwort_hash: Mapped[str] = mapped_column(String(255))
    erstellt_am: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=jetzt)

    mitgliedschaften: Mapped[list[Mitgliedschaft]] = relationship(
        back_populates="nutzer", cascade="all, delete-orphan"
    )


class Terminart(StrEnum):
    TRAINING = "training"
    SPIEL = "spiel"
    EVENT = "event"


class Status(StrEnum):
    ZUGESAGT = "zugesagt"
    ABGESAGT = "abgesagt"
    VIELLEICHT = "vielleicht"
    OFFEN = "offen"


class Team(Basis):
    __tablename__ = "team"

    id: Mapped[int] = mapped_column(primary_key=True)
    name: Mapped[str] = mapped_column(String(100))
    erstellt_am: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=jetzt)
    # Antwort, die gilt, solange ein Mitglied nicht selbst geantwortet hat („offen“ oder „zugesagt“).
    standard_training: Mapped[str] = mapped_column(String(20), default=Status.OFFEN, server_default=Status.OFFEN)
    standard_spiel: Mapped[str] = mapped_column(String(20), default=Status.OFFEN, server_default=Status.OFFEN)
    standard_event: Mapped[str] = mapped_column(String(20), default=Status.OFFEN, server_default=Status.OFFEN)

    def standard(self, art: str) -> str:
        return {
            Terminart.TRAINING: self.standard_training,
            Terminart.SPIEL: self.standard_spiel,
            Terminart.EVENT: self.standard_event,
        }[Terminart(art)]

    mitgliedschaften: Mapped[list[Mitgliedschaft]] = relationship(
        back_populates="team", cascade="all, delete-orphan"
    )
    einladungen: Mapped[list[Einladung]] = relationship(
        back_populates="team", cascade="all, delete-orphan"
    )


class Mitgliedschaft(Basis):
    __tablename__ = "mitgliedschaft"
    __table_args__ = (UniqueConstraint("nutzer_id", "team_id"),)

    id: Mapped[int] = mapped_column(primary_key=True)
    nutzer_id: Mapped[int] = mapped_column(ForeignKey("nutzer.id", ondelete="CASCADE"), index=True)
    team_id: Mapped[int] = mapped_column(ForeignKey("team.id", ondelete="CASCADE"), index=True)
    rolle: Mapped[str] = mapped_column(String(20), default=Rolle.SPIELER)
    beigetreten_am: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=jetzt)

    nutzer: Mapped[Nutzer] = relationship(back_populates="mitgliedschaften")
    team: Mapped[Team] = relationship(back_populates="mitgliedschaften")


class Einladung(Basis):
    __tablename__ = "einladung"

    id: Mapped[int] = mapped_column(primary_key=True)
    team_id: Mapped[int] = mapped_column(ForeignKey("team.id", ondelete="CASCADE"), index=True)
    code: Mapped[str] = mapped_column(String(16), unique=True)
    rolle: Mapped[str] = mapped_column(String(20), default=Rolle.SPIELER)
    erstellt_von_id: Mapped[int | None] = mapped_column(
        ForeignKey("nutzer.id", ondelete="SET NULL")
    )
    erstellt_am: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=jetzt)
    gueltig_bis: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    # None = beliebig oft nutzbar (z. B. Code für die ganze Mannschaft).
    max_nutzungen: Mapped[int | None] = mapped_column(Integer)
    nutzungen: Mapped[int] = mapped_column(Integer, default=0)

    team: Mapped[Team] = relationship(back_populates="einladungen")


class Token(Basis):
    """Anmelde-Token eines Geräts. Gespeichert wird nur der SHA-256-Hash."""

    __tablename__ = "token"

    id: Mapped[int] = mapped_column(primary_key=True)
    nutzer_id: Mapped[int] = mapped_column(ForeignKey("nutzer.id", ondelete="CASCADE"), index=True)
    token_hash: Mapped[str] = mapped_column(String(64), unique=True)
    geraet: Mapped[str | None] = mapped_column(String(100))
    erstellt_am: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=jetzt)
    gueltig_bis: Mapped[datetime] = mapped_column(DateTime(timezone=True))

    nutzer: Mapped[Nutzer] = relationship()


class Termin(Basis):
    __tablename__ = "termin"

    id: Mapped[int] = mapped_column(primary_key=True)
    team_id: Mapped[int] = mapped_column(ForeignKey("team.id", ondelete="CASCADE"), index=True)
    # Gemeinsame Kennung aller Termine einer Serie (z. B. Training jeden Di + Do).
    serie_id: Mapped[str | None] = mapped_column(String(32), index=True)
    art: Mapped[str] = mapped_column(String(20))
    titel: Mapped[str | None] = mapped_column(String(100))
    beginn: Mapped[datetime] = mapped_column(DateTime(timezone=True), index=True)
    ende: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    treffen_vorher_min: Mapped[int | None] = mapped_column(Integer)
    ort: Mapped[str | None] = mapped_column(String(200))
    notiz: Mapped[str | None] = mapped_column(Text)
    gegner: Mapped[str | None] = mapped_column(String(100))
    heimspiel: Mapped[bool | None] = mapped_column(Boolean)
    # Antworten sind bis so viele Stunden vor Beginn möglich (sonst bis Beginn).
    frist_stunden: Mapped[int | None] = mapped_column(Integer)
    abgesagt: Mapped[bool] = mapped_column(Boolean, default=False, server_default=false())
    absage_grund: Mapped[str | None] = mapped_column(String(200))
    erstellt_von_id: Mapped[int | None] = mapped_column(ForeignKey("nutzer.id", ondelete="SET NULL"))
    erstellt_am: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=jetzt)

    team: Mapped[Team] = relationship()
    antworten: Mapped[list[Antwort]] = relationship(back_populates="termin", cascade="all, delete-orphan")


class Antwort(Basis):
    """Zu-/Absage eines Mitglieds zu einem Termin."""

    __tablename__ = "antwort"
    __table_args__ = (UniqueConstraint("termin_id", "nutzer_id"),)

    id: Mapped[int] = mapped_column(primary_key=True)
    termin_id: Mapped[int] = mapped_column(ForeignKey("termin.id", ondelete="CASCADE"), index=True)
    nutzer_id: Mapped[int] = mapped_column(ForeignKey("nutzer.id", ondelete="CASCADE"), index=True)
    status: Mapped[str] = mapped_column(String(20))
    grund: Mapped[str | None] = mapped_column(String(200))
    # Wer die Antwort eingetragen hat (Trainer können für Spieler eintragen).
    eingetragen_von_id: Mapped[int | None] = mapped_column(ForeignKey("nutzer.id", ondelete="SET NULL"))
    geaendert_am: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=jetzt, onupdate=jetzt)

    termin: Mapped[Termin] = relationship(back_populates="antworten")
