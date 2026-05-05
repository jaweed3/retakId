# Retak.id — Hermetic Training Environment
# Build: docker build -t retakid-train -f backend/Dockerfile .
# Run:   docker run -v $(pwd)/backend/data:/app/backend/data -v $(pwd)/backend/models:/app/backend/models -v $(pwd)/backend/logs:/app/backend/logs retakid-train
#
# Lab PC zero-setup:
#   docker build -t retakid-train -f backend/Dockerfile . && docker run retakid-train

FROM python:3.11-slim

RUN apt-get update && apt-get install -y --no-install-recommends \
    libgl1 \
    libglib2.0-0 \
    git \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Install uv
COPY --from=ghcr.io/astral-sh/uv:latest /uv /usr/local/bin/uv

# Install dependencies (layer cached unless pyproject.toml changes)
COPY pyproject.toml uv.lock* ./
ENV UV_SYSTEM_PYTHON=1
RUN uv sync --python 3.11

# Copy project files
COPY .dvc/ .dvc/
COPY .dvcignore ./
COPY Makefile ./
COPY backend/ backend/
COPY scripts/ scripts/

ENV PYTHONPATH=/app

# Pull data from DagsHub, then train
CMD ["sh", "-c", "uv run dvc pull && uv run python backend/src/training/train.py"]
