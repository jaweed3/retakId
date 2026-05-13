#!/usr/bin/env bash
set -euo pipefail

# Copy TFLite model ke web-app public directory untuk client-side inference.
# Pakai LiteRT.js — TFLite langsung jalan di browser pake WebAssembly/XNNPack,
# gak perlu convert ke format TFJS.
#
# Usage:
#   bash scripts/deploy_model_web.sh

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

SRC="$PROJECT_ROOT/mobile-app/app/src/main/assets/retak_mobilenetv2.tflite"
DST="$PROJECT_ROOT/web-app/public/models/retak/retak_mobilenetv2.tflite"

if [ ! -f "$SRC" ]; then
  echo "✗ TFLite not found at $SRC"
  exit 1
fi

mkdir -p "$(dirname "$DST")"
cp "$SRC" "$DST"

echo "✓ Copied $(du -h "$SRC" | cut -f1) → $DST"
