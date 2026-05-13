#!/usr/bin/env python3
"""Convert INT8 TFLite → TFJS Graph Model for web-app.

Monkey-patches numpy for tensorflowjs compatibility (np.object/np.bool
removed in numpy 2.x), then runs the converter.

Usage:
    python scripts/convert_model_web.py
    python scripts/convert_model_web.py --tflite path/to/model.tflite
"""

import sys
import argparse
from pathlib import Path

# --- numpy compat: restore aliases removed in numpy 2.x ---
import numpy as np
np.object = object
np.bool = bool

# --- now safe to import tensorflowjs ---
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
        print(f"  {f.name} ({f.stat().st_size / 1024:.0f} KB)")


if __name__ == "__main__":
    main()
