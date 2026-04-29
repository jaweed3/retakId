# Retak.id — Hermetic Training Environment
# Build: docker build -t retakid-train -f backend/Dockerfile .
# Run:   docker run -v $(pwd)/backend/data:/app/backend/data -v $(pwd)/backend/models:/app/backend/models retakid-train

FROM python:3.11-slim

RUN apt-get update && apt-get install -y --no-install-recommends \
    libgl1 \
    libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Install uv
COPY --from=ghcr.io/astral-sh/uv:latest /uv /usr/local/bin/uv

# Install dependencies
COPY pyproject.toml uv.lock* ./
COPY backend/ backend/

ENV UV_SYSTEM_PYTHON=1
RUN uv sync --python 3.11

ENV PYTHONPATH=/app

CMD ["uv", "run", "python", "backend/src/training/train.py"]
