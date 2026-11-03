"""Passwörter, Tokens und Einladungscodes."""

from __future__ import annotations

import hashlib
import secrets

from argon2 import PasswordHasher
from argon2.exceptions import InvalidHashError, VerificationError

_hasher = PasswordHasher()

# Ohne leicht verwechselbare Zeichen (0/O, 1/I/L), damit Codes gut abzutippen sind.
CODE_ZEICHEN = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
CODE_LAENGE = 8


def passwort_hashen(passwort: str) -> str:
    return _hasher.hash(passwort)


def passwort_pruefen(passwort_hash: str, passwort: str) -> bool:
    try:
        return _hasher.verify(passwort_hash, passwort)
    except (VerificationError, InvalidHashError):
        return False


# Wird bei unbekannter E-Mail geprüft, damit die Antwortzeit nichts verrät.
BLINDER_HASH = passwort_hashen(secrets.token_hex(16))


def token_erzeugen() -> str:
    return secrets.token_urlsafe(32)


def token_hash(token: str) -> str:
    return hashlib.sha256(token.encode()).hexdigest()


def code_erzeugen() -> str:
    return "".join(secrets.choice(CODE_ZEICHEN) for _ in range(CODE_LAENGE))


def code_normalisieren(code: str) -> str:
    """„abcd-efgh“ → „ABCDEFGH“ (Bindestriche und Leerzeichen sind nur Lesehilfe)."""
    return "".join(z for z in code.upper() if z.isalnum())
