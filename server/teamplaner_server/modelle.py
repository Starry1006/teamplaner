"""Datenmodell."""

from __future__ import annotations

from datetime import datetime
from enum import StrEnum

from sqlalchemy import DateTime, ForeignKey, Integer, String, UniqueConstraint
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


class Team(Basis):
    __tablename__ = "team"

    id: Mapped[int] = mapped_column(primary_key=True)
    name: Mapped[str] = mapped_column(String(100))
    erstellt_am: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=jetzt)

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
