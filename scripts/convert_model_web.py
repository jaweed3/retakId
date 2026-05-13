#!/usr/bin/env uv run
# /// script
# requires-python = ">=3.10"
# dependencies = [
#     "tensorflowjs==4.21.0",
#     "numpy<2",
#     "tensorflow<2.19",
# ]
# ///
"""Convert INT8 TFLite → TFJS Graph Model for web-app.

Uses uv to auto-create an isolated environment — no dependency conflicts
with the main project.

Usage:
    uv run scripts/convert_model_web.py
    uv run scripts/convert_model_web.py --tflite path/to/model.tflite
"""

import sys
import argparse
from pathlib import Path
from tensorflowjs.converters.converter import convert  # noqa: E402


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Convert TFLite model to TFJS Graph Model"
    )
    parser.add_argument(
        "--tflite",
        default=str(
            Path(__file__).resolve().parent.parent
            / "mobile-app/app/src/main/assets/retak_mobilenetv2.tflite"
        ),
        help="Path to TFLite model file",
    )
    parser.add_argument(
        "--output",
        default=str(
            Path(__file__).resolve().parent.parent
            / "web-app/public/models/retak"
        ),
        help="Output directory for TFJS model",
    )
    args = parser.parse_args()

    tflite_path = Path(args.tflite)
    if not tflite_path.exists():
        print(f"✗ TFLite not found: {tflite_path}")
        sys.exit(1)

    output_dir = Path(args.output)
    output_dir.mkdir(parents=True, exist_ok=True)

    print(f"Converting: {tflite_path} → {output_dir}")
    convert(
        input_format="tflite",
        output_format="tfjs_graph_model",
        input_path=str(tflite_path),
        output_path=str(output_dir),
    )

    print("✓ Done! Files:")
    for f in sorted(output_dir.iterdir()):
        if f.is_file() and f.name != ".gitkeep":
            print(f"  {f.name} ({f.stat().st_size / 1024:.0f} KB)")


if __name__ == "__main__":
    main()
