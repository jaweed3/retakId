#!/usr/bin/env bash
set -euo pipefail

# Convert INT8 TFLite → TFJS Graph Model untuk web-app.
# Jalankan di device yang punya tensorflowjs terinstall.
#
# Usage:
#   bash scripts/convert_model_web.sh
#   bash scripts/convert_model_web.sh --tflite path/to/model.tflite

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

TFLITE_PATH="${1:-$PROJECT_ROOT/mobile-app/app/src/main/assets/retak_mobilenetv2.tflite}"
OUTPUT_DIR="$PROJECT_ROOT/web-app/public/models/retak"

if [ ! -f "$TFLITE_PATH" ]; then
  echo "✗ TFLite not found at $TFLITE_PATH"
  echo "  Usage: $0 [--tflite /path/to/model.tflite]"
  exit 1
fi

# tensorflowjs blm kompatibel dgn numpy 2.x — pin ke <2
if python -c "import numpy; assert numpy.__version__ >= '2'" 2>/dev/null; then
  echo "→ numpy 2.x terdeteksi, pin ke numpy<2 untuk konversi..."
  pip install 'numpy<2' -q
fi
mkdir -p "$OUTPUT_DIR"

echo "Converting: $TFLITE_PATH → $OUTPUT_DIR"

tensorflowjs_converter \
  --input_format=tflite \
  --output_format=tfjs_graph_model \
  "$TFLITE_PATH" \
  "$OUTPUT_DIR"

echo "✓ Done! Files in $OUTPUT_DIR:"
ls -lh "$OUTPUT_DIR"
