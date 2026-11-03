FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /app

COPY pyproject.toml ./
COPY teamplaner_server ./teamplaner_server
RUN pip install --no-cache-dir . && useradd --system --uid 1000 teamplaner

USER teamplaner
EXPOSE 8000
# Bringt die Datenbank auf den neuesten Stand und startet dann den Server.
CMD ["teamplaner-server", "starten", "--port", "8000"]
