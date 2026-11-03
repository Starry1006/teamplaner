# Teamplaner – Konzept

Ziel: eine schlanke Team-App für den eigenen Verein, ähnlich den bekannten
Teammanagement-Apps, aber mit allen Daten auf dem eigenen Server.

## Rahmen

| Thema | Entscheidung |
| --- | --- |
| Zielgruppe | eigener Verein, ein oder mehrere Teams |
| App | Android, Kotlin + Jetpack Compose, Material 3, hell/dunkel |
| Server | Python/FastAPI, PostgreSQL, Docker Compose, Caddy (HTTPS) |
| Anmeldung | E-Mail + Passwort; Konto nur über Einladungscode |
| Datenschutz | alles auf dem eigenen Server; keine Werbung, kein Tracking |

## Rollen

| | Admin | Trainer | Spieler |
| --- | :-: | :-: | :-: |
| Mitglieder sehen | ✔ | ✔ | ✔ |
| E-Mail-Adressen sehen | ✔ | ✔ | nur eigene |
| Spieler einladen | ✔ | ✔ | |
| Trainer/Admins einladen | ✔ | | |
| Rollen ändern, Mitglieder entfernen | ✔ | | |
| Team umbenennen | ✔ | | |
| Termine anlegen (AP 2) | ✔ | ✔ | |
| Kasse buchen (AP 3) | ✔ | ✔ (Kassenwart später eigene Rolle?) | |
| Umfragen erstellen (AP 4) | ✔ | ✔ | |

Ein Team hat immer mindestens einen Admin. Jedes Mitglied kann selbst austreten.

## Arbeitspakete

1. **Grundgerüst** ✔ – Datenmodell, Login, Einladungscodes, Teamliste, Rollen, Profil.
2. **Termine** ✔ – Training/Spiel/Event, Serien (z. B. jeden Di + Do), Ort, Treffpunkt;
   Zu-/Absage mit Grund, Standardantwort pro Termintyp, Antwortfrist, Übersicht
   für Trainer (wer fehlt noch?), Beteiligung seit Saisonbeginn.
   *Noch offen:* Terminliste offline verfügbar (später mit Room-Zwischenspeicher).
3. **Kasse** – Beiträge, Strafenkatalog, Buchungen pro Mitglied, offene Beträge,
   Kassenstand, Export als CSV.
4. **Umfragen** – Einfach-/Mehrfachauswahl, anonym oder namentlich, Frist, Ergebnis.
5. **Feinschliff** – Push-Benachrichtigungen (FCM nur als „es gibt Neues“-Signal,
   Inhalte bleiben auf dem Server), Erinnerungen, Kalender-Export (iCal),
   signierte Release-APK.

## Termine – Regeln

- Spieler antworten bis zur Frist (ohne Frist: bis Beginn); danach und bei abgesagten
  Terminen nur noch Trainer/Admins.
- Absagegründe sehen nur Trainer, Admins und die Person selbst.
- „Ab hier ändern“ überträgt eine neue Uhrzeit als Verschiebung auf alle folgenden
  Termine der Serie; andere Felder werden nur gesetzt, wenn sie geändert wurden.
- Beteiligung zählt vergangene, nicht abgesagte Termine – pro Mitglied erst ab Beitritt.

## Ideen für Fußball (später)

- **Kader/Nominierung** für Spiele: Trainer wählt aus den Zusagen den Spieltagskader
  (z. B. 16 von 25) und teilt ihn.
- Positionen (Tor, Abwehr, Mittelfeld, Sturm) in der Teamliste.
- Fahrgemeinschaften zu Auswärtsspielen.

## Offene Fragen

- Sportart: Fußball, ca. 25 Spieler ✔
- Welcher Server (VPS, NAS, Homeserver)?
- Push über Firebase Cloud Messaging in Ordnung?
