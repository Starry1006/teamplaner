"""Kommandozeile: Datenbank migrieren, ersten Admin einrichten, Server starten."""

from __future__ import annotations

import argparse
import getpass
import os
import sys
from pathlib import Path

from sqlalchemy import select
from sqlalchemy.orm import Session

from .datenbank import engine_erstellen
from .einstellungen import laden
from .modelle import Mitgliedschaft, Nutzer, Rolle, Team
from .schemas import PASSWORT_MIN
from .sicherheit import passwort_hashen

PAKET = Path(__file__).parent


def migrieren() -> None:
    from alembic import command
    from alembic.config import Config

    konfig = Config(str(PAKET / "alembic.ini"))
    konfig.set_main_option("script_location", str(PAKET / "migrationen"))
    konfig.set_main_option("sqlalchemy.url", laden().datenbank_url.replace("%", "%%"))
    command.upgrade(konfig, "head")


def einrichten(team_name: str, name: str, email: str) -> None:
    passwort = os.environ.get("TEAMPLANER_PASSWORT") or getpass.getpass("Passwort: ")
    if len(passwort) < PASSWORT_MIN:
        sys.exit(f"Passwort muss mindestens {PASSWORT_MIN} Zeichen haben.")
    email = email.strip().lower()
    with Session(engine_erstellen(laden().datenbank_url)) as db:
        nutzer = db.scalar(select(Nutzer).where(Nutzer.email == email))
        if nutzer is None:
            nutzer = Nutzer(name=name, email=email, passwort_hash=passwort_hashen(passwort))
        team = Team(name=team_name)
        db.add(Mitgliedschaft(nutzer=nutzer, team=team, rolle=Rolle.ADMIN))
        db.commit()
        print(f"Team „{team.name}“ (ID {team.id}) angelegt, Admin: {nutzer.email}")


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(prog="teamplaner-server")
    befehle = parser.add_subparsers(dest="befehl", required=True)

    befehle.add_parser("migrieren", help="Datenbank auf den neuesten Stand bringen")

    e = befehle.add_parser("einrichten", help="Team mit erstem Admin anlegen")
    e.add_argument("--team", required=True, help="Name des Teams")
    e.add_argument("--name", required=True, help="Name des Admins")
    e.add_argument("--email", required=True, help="E-Mail des Admins (Anmeldename)")

    s = befehle.add_parser("starten", help="Migrieren und Server starten")
    s.add_argument("--host", default="0.0.0.0")
    s.add_argument("--port", type=int, default=8000)

    args = parser.parse_args(argv)
    if args.befehl == "migrieren":
        migrieren()
    elif args.befehl == "einrichten":
        einrichten(args.team, args.name, args.email)
    elif args.befehl == "starten":
        import uvicorn

        migrieren()
        uvicorn.run(
            "teamplaner_server.app:erstelle_app",
            factory=True,
            host=args.host,
            port=args.port,
            proxy_headers=True,
        )


if __name__ == "__main__":
    main()
