from conftest import einladen, registrieren


def test_mitgliederliste_sortiert_und_email_nur_fuer_trainer(client, team_mit_admin):
    team_id, admin = team_mit_admin
    trainer = registrieren(client, einladen(client, team_id, admin.kopf, rolle="trainer"), "Tom", "tom@example.org")
    spieler = registrieren(client, einladen(client, team_id, admin.kopf), "Ben", "ben@example.org")

    liste = client.get(f"/teams/{team_id}/mitglieder", headers=spieler.kopf).json()
    assert [(m["name"], m["rolle"]) for m in liste] == [
        ("Anna Admin", "admin"),
        ("Tom", "trainer"),
        ("Ben", "spieler"),
    ]
    assert [m["email"] for m in liste] == [None, None, "ben@example.org"]

    liste = client.get(f"/teams/{team_id}/mitglieder", headers=trainer.kopf).json()
    assert all(m["email"] for m in liste)


def test_fremdes_team_ist_unsichtbar(client, team_mit_admin):
    team_id, admin = team_mit_admin
    andere = client.post("/teams", json={"name": "Damen"}, headers=admin.kopf).json()
    spieler = registrieren(client, einladen(client, team_id, admin.kopf), "Ben", "ben@example.org")
    assert client.get(f"/teams/{andere['id']}/mitglieder", headers=spieler.kopf).status_code == 404


def test_team_anlegen_macht_zum_admin(client, team_mit_admin):
    _, admin = team_mit_admin
    r = client.post("/teams", json={"name": "A-Jugend"}, headers=admin.kopf)
    assert r.status_code == 201
    assert r.json()["rolle"] == "admin"
    namen = [t["name"] for t in client.get("/ich", headers=admin.kopf).json()["teams"]]
    assert namen == ["1. Herren", "A-Jugend"]


def test_rechte_spieler_und_trainer(client, team_mit_admin):
    team_id, admin = team_mit_admin
    trainer = registrieren(client, einladen(client, team_id, admin.kopf, rolle="trainer"), "Tom", "tom@example.org")
    spieler = registrieren(client, einladen(client, team_id, admin.kopf), "Ben", "ben@example.org")

    # Spieler darf weder einladen noch Rollen ändern noch umbenennen.
    assert client.post(f"/teams/{team_id}/einladungen", json={}, headers=spieler.kopf).status_code == 403
    assert client.patch(
        f"/teams/{team_id}/mitglieder/{trainer.nutzer_id}", json={"rolle": "spieler"}, headers=spieler.kopf
    ).status_code == 403
    assert client.patch(f"/teams/{team_id}", json={"name": "X"}, headers=spieler.kopf).status_code == 403

    # Trainer darf nur Spieler einladen und keine Rollen ändern.
    assert client.post(f"/teams/{team_id}/einladungen", json={}, headers=trainer.kopf).status_code == 201
    assert client.post(
        f"/teams/{team_id}/einladungen", json={"rolle": "admin"}, headers=trainer.kopf
    ).status_code == 403
    assert client.delete(
        f"/teams/{team_id}/mitglieder/{spieler.nutzer_id}", headers=trainer.kopf
    ).status_code == 403


def test_rolle_aendern_und_letzter_admin(client, team_mit_admin):
    team_id, admin = team_mit_admin
    ben = registrieren(client, einladen(client, team_id, admin.kopf), "Ben", "ben@example.org")
    url_admin = f"/teams/{team_id}/mitglieder/{admin.nutzer_id}"

    # Einziger Admin kann sich weder herabstufen noch austreten.
    assert client.patch(url_admin, json={"rolle": "spieler"}, headers=admin.kopf).status_code == 409
    assert client.delete(url_admin, headers=admin.kopf).status_code == 409

    assert client.patch(
        f"/teams/{team_id}/mitglieder/{ben.nutzer_id}", json={"rolle": "admin"}, headers=admin.kopf
    ).status_code == 204
    # Jetzt gibt es einen zweiten Admin – Austreten geht.
    assert client.delete(url_admin, headers=admin.kopf).status_code == 204
    assert client.get(f"/teams/{team_id}", headers=admin.kopf).status_code == 404


def test_spieler_kann_austreten_admin_kann_entfernen(client, team_mit_admin):
    team_id, admin = team_mit_admin
    ben = registrieren(client, einladen(client, team_id, admin.kopf), "Ben", "ben@example.org")
    cem = registrieren(client, einladen(client, team_id, admin.kopf), "Cem", "cem@example.org")
    assert client.delete(f"/teams/{team_id}/mitglieder/{ben.nutzer_id}", headers=ben.kopf).status_code == 204
    assert client.delete(f"/teams/{team_id}/mitglieder/{cem.nutzer_id}", headers=admin.kopf).status_code == 204
    namen = [m["name"] for m in client.get(f"/teams/{team_id}/mitglieder", headers=admin.kopf).json()]
    assert namen == ["Anna Admin"]


def test_einladung_max_nutzungen_und_liste(client, team_mit_admin):
    team_id, admin = team_mit_admin
    code = einladen(client, team_id, admin.kopf, max_nutzungen=1)
    einladen(client, team_id, admin.kopf)
    assert len(client.get(f"/teams/{team_id}/einladungen", headers=admin.kopf).json()) == 2

    registrieren(client, code, "Ben", "ben@example.org")
    r = client.post(
        "/auth/registrieren",
        json={"code": code, "name": "Cem", "email": "cem@example.org", "passwort": "geheim1234"},
    )
    assert r.status_code == 400
    # Verbrauchte Einladung taucht nicht mehr auf.
    assert len(client.get(f"/teams/{team_id}/einladungen", headers=admin.kopf).json()) == 1


def test_einladung_loeschen(client, team_mit_admin):
    team_id, admin = team_mit_admin
    einladen(client, team_id, admin.kopf)
    eid = client.get(f"/teams/{team_id}/einladungen", headers=admin.kopf).json()[0]["id"]
    assert client.delete(f"/teams/{team_id}/einladungen/{eid}", headers=admin.kopf).status_code == 204
    assert client.get(f"/teams/{team_id}/einladungen", headers=admin.kopf).json() == []


def test_code_einloesen_bestehendes_konto(client, team_mit_admin):
    team_id, admin = team_mit_admin
    ben = registrieren(client, einladen(client, team_id, admin.kopf), "Ben", "ben@example.org")
    damen = client.post("/teams", json={"name": "Damen"}, headers=admin.kopf).json()
    code = einladen(client, damen["id"], admin.kopf, rolle="trainer")

    r = client.post("/einladungen/einloesen", json={"code": code}, headers=ben.kopf)
    assert r.json() == {"id": damen["id"], "name": "Damen", "rolle": "trainer"}
    # Zweites Mal: schon Mitglied.
    code2 = einladen(client, damen["id"], admin.kopf)
    assert client.post("/einladungen/einloesen", json={"code": code2}, headers=ben.kopf).status_code == 409
