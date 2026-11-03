"""Alembic-Umgebung."""

from logging.config import fileConfig

from alembic import context

from teamplaner_server.datenbank import Basis, engine_erstellen
from teamplaner_server.einstellungen import laden
from teamplaner_server import modelle  # noqa: F401 – registriert die Tabellen

konfig = context.config
if konfig.config_file_name is not None:
    fileConfig(konfig.config_file_name, disable_existing_loggers=False)

url = konfig.get_main_option("sqlalchemy.url") or laden().datenbank_url


def offline() -> None:
    context.configure(url=url, target_metadata=Basis.metadata, literal_binds=True, render_as_batch=True)
    with context.begin_transaction():
        context.run_migrations()


def online() -> None:
    with engine_erstellen(url).connect() as verbindung:
        context.configure(connection=verbindung, target_metadata=Basis.metadata, render_as_batch=True)
        with context.begin_transaction():
            context.run_migrations()


if context.is_offline_mode():
    offline()
else:
    online()
