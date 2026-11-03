# Teamplaner

Android-App für das eigene Team – Termine mit Zu-/Absagen, Mitglieder und Rollen,
Mannschaftskasse und Umfragen. Die Daten liegen auf dem eigenen Server.

- `server/` – REST-API (Python, FastAPI, PostgreSQL)
- `android/` – App (Kotlin, Jetpack Compose)
- `docs/konzept.md` – Funktionen, Rollen, Arbeitspakete

## Stand

**AP 1 – Grundgerüst** ✔
Anmeldung, Registrierung nur mit Einladungscode, Teamliste, Rollen (Admin, Trainer,
Spieler), Einladungen erstellen/teilen/löschen, mehrere Teams, Profil und Passwort.

## Server betreiben

Auf einem Server mit Docker und einer Domain, die auf ihn zeigt:

```bash
cp .env.beispiel .env        # Domain und DB-Passwort eintragen
docker compose up -d --build
docker compose exec server teamplaner-server einrichten --team "1. Herren" --name "Thomas" --email thomas@example.org
```

Caddy holt das HTTPS-Zertifikat automatisch. In der App dann die Domain als Server
eintragen und mit dem Admin-Konto anmelden; weitere Mitglieder lädt man per Code ein.

## Entwicklung

Server:

```bash
cd server
python3.12 -m venv .venv && . .venv/bin/activate
pip install -e ".[dev]"
pytest -q
TEAMPLANER_DB=sqlite:///./dev.db teamplaner-server starten   # http://localhost:8000/docs
```

App (Android Studio oder Kommandozeile, JDK 17+):

```bash
cd android
./gradlew assembleDebug testDebugUnitTest
```

Der Debug-Build erlaubt `http://` (z. B. `http://192.168.1.10:8000` im WLAN), der
Release-Build nur HTTPS. Jeder Push baut über GitHub Actions eine Debug-APK
(Actions → Lauf → Artefakt `teamplaner-debug-apk`).

## Datenbank ändern

Modell in `server/teamplaner_server/modelle.py` anpassen, dann Migration erzeugen:

```bash
cd server/teamplaner_server
alembic -c alembic.ini revision --autogenerate -m "Beschreibung"
```

Der Server spielt Migrationen beim Start automatisch ein.
