"""FastAPI-Anwendung."""

from __future__ import annotations

from fastapi import FastAPI
from sqlalchemy import Engine
from sqlalchemy.orm import sessionmaker

from . import __version__
from .datenbank import engine_erstellen
from .einstellungen import Einstellungen, laden
from .routen import anmeldung, ich, teams, termine


def erstelle_app(einstellungen: Einstellungen | None = None, engine: Engine | None = None) -> FastAPI:
    einstellungen = einstellungen or laden()
    engine = engine or engine_erstellen(einstellungen.datenbank_url)

    app = FastAPI(title="Teamplaner", version=__version__)
    app.state.einstellungen = einstellungen
    app.state.sitzungen = sessionmaker(engine, expire_on_commit=False)

    @app.get("/gesundheit", tags=["Betrieb"])
    def gesundheit() -> dict[str, str]:
        return {"status": "ok", "version": __version__}

    app.include_router(anmeldung.router)
    app.include_router(ich.router)
    app.include_router(teams.router)
    app.include_router(termine.router)
    return app
