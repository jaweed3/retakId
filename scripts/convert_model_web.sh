#!/usr/bin/env bash
set -euo pipefail

# Convert INT8 TFLite → TFJS Graph Model untuk web-app.
# Menggunakan uv run dengan inline metadata — environment terisolasi,
# gak kena dependency hell dari project utama.
#
# Usage:
#   bash scripts/convert_model_web.sh
#   bash scripts/convert_model_web.sh --tflite path/to/model.tflite

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

uv run "$SCRIPT_DIR/convert_model_web.py" "$@"
