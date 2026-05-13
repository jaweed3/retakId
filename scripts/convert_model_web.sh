#!/usr/bin/env bash
set -euo pipefail

# Convert INT8 TFLite → TFJS Graph Model via Docker.
# Menggunakan tensorflow/tensorflow image — zero dependency hell.
#
# Prerequisites: Docker installed and running.
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

mkdir -p "$OUTPUT_DIR"

TFLITE_ABS="$(cd "$(dirname "$TFLITE_PATH")" && pwd)/$(basename "$TFLITE_PATH")"
OUTPUT_ABS="$(cd "$OUTPUT_DIR" && pwd)"

echo "Converting: $TFLITE_ABS → $OUTPUT_ABS"

docker run --rm \
  -v "$TFLITE_ABS:/model.tflite" \
  -v "$OUTPUT_ABS:/output" \
  tensorflow/tensorflow:2.18.0 \
  bash -c "
    pip install -q 'tensorflowjs' 'protobuf>=6.31.1' &&
    tensorflowjs_converter \
      --input_format=tflite \
      --output_format=tfjs_graph_model \
      /model.tflite \
      /output
  "

echo "✓ Done! Files in $OUTPUT_ABS:"
ls -lh "$OUTPUT_ABS"
