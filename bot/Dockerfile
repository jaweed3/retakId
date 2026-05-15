FROM python:3.11-slim

RUN apt-get update && apt-get install -y --no-install-recommends \
    libgl1 \
    libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

COPY --from=ghcr.io/astral-sh/uv:latest /uv /usr/local/bin/uv

ENV UV_SYSTEM_PYTHON=1

WORKDIR /app

COPY bot/pyproject.toml .
RUN uv sync --system --no-dev

COPY bot/src/ src/
COPY backend/models/retak_mobilenetv2.tflite models/retak_mobilenetv2.tflite

ENV PYTHONPATH=/app/src
ENV MODEL_PATH=/app/models/retak_mobilenetv2.tflite

HEALTHCHECK --interval=30s --timeout=10s --start-period=15s --retries=3 \
    CMD python -c "import http.client; http.client.HTTPConnection('localhost', 8080).request('GET', '/health')" || exit 1

CMD ["python", "-m", "main"]
