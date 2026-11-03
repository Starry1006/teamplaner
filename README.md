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

**AP 2 – Termine** ✔
Training, Spiel (Gegner, Heim/Auswärts) und Event; wöchentliche Serien (z. B. Di + Do
bis Saisonende); Treffpunkt, Ort, Notiz, Antwortfrist; Zu-/Absage/Vielleicht mit Grund
(nur für Trainer sichtbar); Trainer tragen Antworten für Spieler ein; Termine absagen,
ändern oder löschen – einzeln oder „ab hier“ für die ganze Serie; Standardantwort pro
Terminart; „Offene Antworten teilen“ (z. B. in die WhatsApp-Gruppe);
Trainings-/Spielbeteiligung seit Saisonbeginn (1. Juli).

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
# optional zusätzlich gegen PostgreSQL (Datenbank wird geleert!):
TEAMPLANER_TEST_DB=postgresql+psycopg://user:pw@localhost/tp_test pytest -q
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

Terminserien werden in der Zeitzone `TEAMPLANER_ZEITZONE` (Standard `Europe/Berlin`)
berechnet – 19:00 Uhr bleibt auch nach der Zeitumstellung 19:00 Uhr.
