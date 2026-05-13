#!/usr/bin/env bash
set -euo pipefail

# Convert INT8 TFLite → TFJS Graph Model untuk web-app.
# Delegates to Python wrapper for numpy compatibility handling.
#
# Usage:
#   bash scripts/convert_model_web.sh
#   bash scripts/convert_model_web.sh --tflite path/to/model.tflite

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

python3 "$SCRIPT_DIR/convert_model_web.py" "$@"
