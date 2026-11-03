"""Einstellungen aus Umgebungsvariablen."""

from __future__ import annotations

import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Einstellungen:
    datenbank_url: str = "sqlite:///./teamplaner.db"
    # Wie lange ein Anmelde-Token gültig bleibt (die App bleibt so lange angemeldet).
    token_tage: int = 180
    # Standard-Gültigkeit neuer Einladungscodes.
    einladung_tage: int = 14
    # Zeitzone für Terminserien (Uhrzeit bleibt über Sommer-/Winterzeit gleich).
    zeitzone: str = "Europe/Berlin"


def laden() -> Einstellungen:
    return Einstellungen(
        datenbank_url=os.environ.get("TEAMPLANER_DB", Einstellungen.datenbank_url),
        token_tage=int(os.environ.get("TEAMPLANER_TOKEN_TAGE", Einstellungen.token_tage)),
        einladung_tage=int(os.environ.get("TEAMPLANER_EINLADUNG_TAGE", Einstellungen.einladung_tage)),
        zeitzone=os.environ.get("TEAMPLANER_ZEITZONE", Einstellungen.zeitzone),
    )
