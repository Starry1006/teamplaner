"""Datenbank-Anbindung (SQLAlchemy)."""

from __future__ import annotations

from datetime import UTC, datetime

from sqlalchemy import Engine, MetaData, create_engine, event
from sqlalchemy.orm import DeclarativeBase

NAMENSKONVENTION = {
    "ix": "ix_%(column_0_label)s",
    "uq": "uq_%(table_name)s_%(column_0_name)s",
    "ck": "ck_%(table_name)s_%(constraint_name)s",
    "fk": "fk_%(table_name)s_%(column_0_name)s_%(referred_table_name)s",
    "pk": "pk_%(table_name)s",
}


class Basis(DeclarativeBase):
    metadata = MetaData(naming_convention=NAMENSKONVENTION)


def jetzt() -> datetime:
    return datetime.now(UTC)


def als_utc(zeitpunkt: datetime) -> datetime:
    """SQLite liefert Zeitpunkte ohne Zeitzone zurück – sie sind immer UTC."""
    return zeitpunkt if zeitpunkt.tzinfo else zeitpunkt.replace(tzinfo=UTC)


def engine_erstellen(url: str) -> Engine:
    if url.startswith("sqlite"):
        engine = create_engine(url, connect_args={"check_same_thread": False})

        @event.listens_for(engine, "connect")
        def _fremdschluessel_an(verbindung, _eintrag):  # pragma: no cover - trivial
            verbindung.execute("PRAGMA foreign_keys=ON")

        return engine
    return create_engine(url, pool_pre_ping=True)
