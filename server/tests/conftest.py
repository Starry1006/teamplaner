from __future__ import annotations

import os
from dataclasses import dataclass

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine, event
from sqlalchemy.orm import Session
from sqlalchemy.pool import StaticPool

from teamplaner_server.app import erstelle_app
from teamplaner_server.datenbank import Basis
from teamplaner_server.einstellungen import Einstellungen
from teamplaner_server.modelle import Mitgliedschaft, Nutzer, Rolle, Team
from teamplaner_server.sicherheit import passwort_hashen

PASSWORT = "geheim1234"


@pytest.fixture
def engine():
    """SQLite im Speicher – oder PostgreSQL, wenn TEAMPLANER_TEST_DB gesetzt ist
    (z. B. postgresql+psycopg://tp:tp@localhost/tp_test; die Datenbank wird geleert!)."""
    url = os.environ.get("TEAMPLANER_TEST_DB")
    if url:
        engine = create_engine(url)
        Basis.metadata.drop_all(engine)
        Basis.metadata.create_all(engine)
        yield engine
        engine.dispose()
        return
    engine = create_engine(
        "sqlite://", connect_args={"check_same_thread": False}, poolclass=StaticPool
    )
    event.listen(engine, "connect", lambda v, _: v.execute("PRAGMA foreign_keys=ON"))
    Basis.metadata.create_all(engine)
    yield engine


@pytest.fixture
def client(engine):
    return TestClient(erstelle_app(Einstellungen(datenbank_url="sqlite://"), engine))


@dataclass
class Konto:
    nutzer_id: int
    email: str
    kopf: dict[str, str]


@pytest.fixture
def team_mit_admin(engine, client) -> tuple[int, Konto]:
    with Session(engine) as db:
        admin = Nutzer(name="Anna Admin", email="anna@example.org", passwort_hash=passwort_hashen(PASSWORT))
        team = Team(name="1. Herren")
        db.add(Mitgliedschaft(nutzer=admin, team=team, rolle=Rolle.ADMIN))
        db.commit()
        team_id, admin_id = team.id, admin.id
    return team_id, anmelden(client, "anna@example.org", admin_id)


def anmelden(client: TestClient, email: str, nutzer_id: int) -> Konto:
    r = client.post("/auth/anmelden", json={"email": email, "passwort": PASSWORT})
    assert r.status_code == 200, r.text
    return Konto(nutzer_id, email, {"Authorization": f"Bearer {r.json()['token']}"})


def einladen(client: TestClient, team_id: int, kopf: dict, **daten) -> str:
    r = client.post(f"/teams/{team_id}/einladungen", json=daten, headers=kopf)
    assert r.status_code == 201, r.text
    return r.json()["code"]


def registrieren(client: TestClient, code: str, name: str, email: str) -> Konto:
    r = client.post(
        "/auth/registrieren",
        json={"code": code, "name": name, "email": email, "passwort": PASSWORT},
    )
    assert r.status_code == 201, r.text
    kopf = {"Authorization": f"Bearer {r.json()['token']}"}
    ich = client.get("/ich", headers=kopf).json()
    return Konto(ich["id"], email, kopf)
