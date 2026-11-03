from conftest import PASSWORT, anmelden, einladen, registrieren


def test_gesundheit(client):
    assert client.get("/gesundheit").json()["status"] == "ok"


def test_ohne_token_401(client):
    assert client.get("/ich").status_code == 401
    assert client.get("/ich", headers={"Authorization": "Bearer falsch"}).status_code == 401


def test_falsches_passwort_und_unbekannte_email(client, team_mit_admin):
    for email, pw in [("anna@example.org", "falsch123"), ("wer@example.org", PASSWORT)]:
        r = client.post("/auth/anmelden", json={"email": email, "passwort": pw})
        assert r.status_code == 401
        assert r.json()["detail"] == "E-Mail oder Passwort falsch"


def test_email_gross_klein_egal(client, team_mit_admin):
    r = client.post("/auth/anmelden", json={"email": "ANNA@Example.org", "passwort": PASSWORT})
    assert r.status_code == 200


def test_registrieren_mit_code_tritt_team_bei(client, team_mit_admin):
    team_id, admin = team_mit_admin
    code = einladen(client, team_id, admin.kopf)
    # Code darf mit Bindestrich und klein eingegeben werden.
    lesbar = f"{code[:4]}-{code[4:]}".lower()
    ben = registrieren(client, lesbar, "  Ben   Becker ", "ben@example.org")
    ich = client.get("/ich", headers=ben.kopf).json()
    assert ich["name"] == "Ben Becker"
    assert ich["teams"] == [{"id": team_id, "name": "1. Herren", "rolle": "spieler"}]


def test_registrieren_ohne_gueltigen_code(client, team_mit_admin):
    r = client.post(
        "/auth/registrieren",
        json={"code": "XXXXXXXX", "name": "X", "email": "x@example.org", "passwort": PASSWORT},
    )
    assert r.status_code == 400


def test_registrieren_doppelte_email(client, team_mit_admin):
    team_id, admin = team_mit_admin
    code = einladen(client, team_id, admin.kopf)
    r = client.post(
        "/auth/registrieren",
        json={"code": code, "name": "A", "email": "Anna@example.org", "passwort": PASSWORT},
    )
    assert r.status_code == 409


def test_passwort_zu_kurz(client, team_mit_admin):
    team_id, admin = team_mit_admin
    code = einladen(client, team_id, admin.kopf)
    r = client.post(
        "/auth/registrieren",
        json={"code": code, "name": "A", "email": "a@example.org", "passwort": "kurz"},
    )
    assert r.status_code == 422


def test_abmelden_macht_token_ungueltig(client, team_mit_admin):
    _, admin = team_mit_admin
    assert client.post("/auth/abmelden", headers=admin.kopf).status_code == 204
    assert client.get("/ich", headers=admin.kopf).status_code == 401


def test_passwort_aendern_meldet_andere_geraete_ab(client, team_mit_admin):
    _, admin = team_mit_admin
    zweites = anmelden(client, admin.email, admin.nutzer_id)
    r = client.put("/ich/passwort", json={"alt": PASSWORT, "neu": "neuesPasswort1"}, headers=admin.kopf)
    assert r.status_code == 204
    assert client.get("/ich", headers=admin.kopf).status_code == 200
    assert client.get("/ich", headers=zweites.kopf).status_code == 401
    r = client.put("/ich/passwort", json={"alt": "falsch", "neu": "nochmalNeu1"}, headers=admin.kopf)
    assert r.status_code == 400
