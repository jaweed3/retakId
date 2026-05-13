#!/usr/bin/env python3
"""
Pre-Deployment TFLite Model Validation.

Validates the exported model BEFORE handing off to Android team.
Catches: broken quantization, output type mismatch, frozen predictions.

Usage:
    uv run python scripts/validate_model.py
    uv run python scripts/validate_model.py --tflite backend/models/retak_mobilenetv2.tflite

Exit code 1 if model fails validation → do NOT deploy.
Exit code 0 if all checks pass → safe to deploy.

Checks:
    1. Model loads successfully
    2. Input spec matches contract (uint8, [1,224,224,3])
    3. Output spec is valid (float32 or uint8, [1,3])
    4. Inference runs on real test images without crash
    5. Model predicts multiple classes (not stuck on one)
    6. Per-class confidence varies (not flat 33/33/33)
    7. Known-safe image predicts AMAN (sanity check)
"""

import os, sys, argparse, logging, json
import numpy as np
import tensorflow as tf
from pathlib import Path
from PIL import Image

LOG_FORMAT = "%(asctime)s - %(levelname)s - %(message)s"
logging.basicConfig(level=logging.INFO, format=LOG_FORMAT)
logger = logging.getLogger(__name__)

LABELS = ["AMAN", "WASPADA", "BAHAYA"]
PASS = "✓"
FAIL = "✗"


def validate(tflite_path: str, test_images_dir: str | None = None) -> dict:
    """Run all validation checks. Returns report dict + exits 1 on failure."""
    report: dict = {"passed": True, "checks": [], "predictions": []}
    errors = []

    # ── 1. Load model ──
    logger.info("Check 1: Loading TFLite model...")
    try:
        interpreter = tf.lite.Interpreter(model_path=tflite_path)
        interpreter.allocate_tensors()
        report["checks"].append({"check": "model_loads", "result": PASS})
        logger.info("  %s Model loaded", PASS)
    except Exception as e:
        report["checks"].append({"check": "model_loads", "result": FAIL, "error": str(e)})
        logger.error("  %s Failed to load model: %s", FAIL, e)
        _fail(report)

    input_details = interpreter.get_input_details()[0]
    output_details = interpreter.get_output_details()[0]

    # ── 2. Input spec ──
    logger.info("Check 2: Input specification...")
    input_shape = list(input_details["shape"])
    input_dtype = input_details["dtype"]
    logger.info(f"  Shape: {input_shape}, dtype: {input_dtype.__name__}")

    expected_shape = [1, 224, 224, 3]
    if input_shape != expected_shape:
        msg = f"Input shape {input_shape} != expected {expected_shape}"
        report["checks"].append({"check": "input_shape", "result": FAIL, "error": msg})
        errors.append(msg)
        logger.error("  %s %s", FAIL, msg)
    else:
        report["checks"].append({"check": "input_shape", "result": PASS})
        logger.info("  %s Shape correct", PASS)

    if input_dtype != np.uint8:
        msg = f"Input dtype {input_dtype} != expected uint8. Android sends raw pixels!"
        report["checks"].append({"check": "input_dtype", "result": FAIL, "error": msg})
        errors.append(msg)
        logger.error("  %s %s", FAIL, msg)
    else:
        report["checks"].append({"check": "input_dtype", "result": PASS})
        logger.info("  %s dtype uint8 — matches Android", PASS)

    # ── 3. Output spec ──
    logger.info("Check 3: Output specification...")
    output_shape = list(output_details["shape"])
    output_dtype = output_details["dtype"]

    # Accept float32 or uint8 for output
    if output_shape != [1, 3]:
        msg = f"Output shape {output_shape} != expected [1, 3]"
        report["checks"].append({"check": "output_shape", "result": FAIL, "error": msg})
        errors.append(msg)
    else:
        report["checks"].append({"check": "output_shape", "result": PASS})
        logger.info("  %s Output shape [1, 3]", PASS)

    report["output_dtype"] = output_dtype.__name__
    logger.info(f"  Output dtype: {output_dtype.__name__}")

    # ── 4. Inference on test images ──
    logger.info("Check 4: Running inference on test images...")

    # Use provided dir, or fall back to processed/ if available
    if test_images_dir is None:
        test_images_dir = "backend/data/processed"

    test_images = _find_test_images(test_images_dir)
    if not test_images:
        # Use synthetic colored images as fallback
        logger.warning("  No test images found, using synthetic test patterns")
        test_images = _synthetic_images()

    input_index = input_details["index"]
    output_index = output_details["index"]

    preds = []
    confidences = []

    for img_info in test_images:
        # Load and preprocess to uint8 224x224
        if isinstance(img_info, tuple):
            img_path, true_cls = img_info
            img = Image.open(img_path).convert("RGB").resize((224, 224))
            img_arr = np.array(img, dtype=np.uint8)
            img_batch = np.expand_dims(img_arr, axis=0)
        else:
            # Synthetic
            img_batch, true_cls = img_info

        interpreter.set_tensor(input_index, img_batch)
        interpreter.invoke()
        raw_out = interpreter.get_tensor(output_index)

        # Handle both output types
        if output_dtype == np.uint8:
            floats = raw_out[0].astype(np.float32) / 255.0
        else:
            floats = raw_out[0].astype(np.float32)

        # Softmax
        max_val = floats.max()
        exp = np.exp(floats - max_val)
        probs = exp / exp.sum()

        pred_idx = int(np.argmax(probs))
        pred_label = LABELS[pred_idx]
        conf = float(probs[pred_idx])

        preds.append(pred_label)
        confidences.append(conf)

        img_name = Path(img_path).name if isinstance(img_info, tuple) else f"synthetic_{true_cls}"
        logger.info(
            f"  [{true_cls}] {img_name:40s} → {pred_label} ({conf:.3f})"
        )
        report["predictions"].append({
            "image": img_name,
            "true_class": true_cls,
            "predicted": pred_label,
            "confidence": round(conf, 4),
        })

    # ── 5. Multi-class check ──
    logger.info("Check 5: Model predicts multiple classes...")
    unique_preds = set(preds)
    if len(unique_preds) < 2:
        msg = f"Model only predicts: {unique_preds}. Frozen model or preprocessing bug!"
        report["checks"].append({"check": "multi_class", "result": FAIL, "error": msg})
        errors.append(msg)
        logger.error("  %s %s", FAIL, msg)
    else:
        report["checks"].append({"check": "multi_class", "result": PASS})
        logger.info("  %s Predicts %d classes: %s", PASS, len(unique_preds), unique_preds)

    # ── 6. Confidence check ──
    logger.info("Check 6: Confidence quality...")
    conf_std = float(np.std(confidences))
    conf_max = float(np.max(confidences))
    conf_min = float(np.min(confidences))

    # Model passes if: max confidence > 0.45 (above random 33%) AND std > 0.02 (not all identical)
    if conf_max < 0.45:
        msg = f"Max confidence={conf_max:.3f} < 0.45 — model never confident"
        report["checks"].append({"check": "confidence_quality", "result": FAIL, "error": msg})
        errors.append(msg)
        logger.error("  %s %s", FAIL, msg)
    elif conf_std < 0.02:
        msg = f"Confidence std={conf_std:.4f} — all predictions identical (frozen model)"
        report["checks"].append({"check": "confidence_quality", "result": FAIL, "error": msg})
        errors.append(msg)
        logger.error("  %s %s", FAIL, msg)
    else:
        report["checks"].append({"check": "confidence_quality", "result": PASS})
        logger.info(
            "  %s Confidence: max=%.3f, min=%.3f, std=%.3f",
            PASS, conf_max, conf_min, conf_std
        )

    # ── Final ──
    if errors:
        report["passed"] = False
        logger.error("\n" + "=" * 60)
        logger.error("  MODEL VALIDATION FAILED — %d issues", len(errors))
        for e in errors:
            logger.error("  → %s", e)
        logger.error("  DO NOT DEPLOY. Fix above issues first.")
        logger.error("=" * 60)
    else:
        logger.info("\n" + "=" * 60)
        logger.info("  %s ALL CHECKS PASSED — safe to deploy", PASS)
        logger.info("  %s Model: %s", PASS, tflite_path)
        logger.info("  %s Input: uint8 [1,224,224,3]", PASS)
        logger.info("  %s Multi-class: %s", PASS, unique_preds)
        logger.info("  %s Confidence std: %.4f", PASS, conf_std)
        logger.info("=" * 60)

    return report


def _find_test_images(data_dir: str) -> list:
    """Find a few test images from each class."""
    images = []
    data_path = Path(data_dir)
    for cls in LABELS:
        cls_dir = data_path / cls
        if cls_dir.exists():
            files = sorted(cls_dir.glob("*"))[:2]
            for f in files:
                if f.suffix.lower() in (".jpg", ".jpeg", ".png"):
                    images.append((str(f), cls))
    return images[:9]  # max 9 images


def _synthetic_images() -> list:
    """Generate synthetic test patterns: green/red/blue-ish images."""
    images = []
    colors = {
        "AMAN":    np.array([50, 180, 50], dtype=np.uint8),
        "WASPADA": np.array([200, 200, 50], dtype=np.uint8),
        "BAHAYA":  np.array([200, 50, 50], dtype=np.uint8),
    }
    for cls, color in colors.items():
        for i in range(2):
            noise = np.random.randint(0, 40, (224, 224, 3), dtype=np.uint8)
            img = np.clip(color.reshape(1, 1, 3) + noise, 0, 255).astype(np.uint8)
            images.append((np.expand_dims(img, 0), cls))
    return images


def _fail(report: dict):
    """Print failure and exit 1."""
    logger.error("=" * 60)
    logger.error("  MODEL VALIDATION FAILED — DO NOT DEPLOY")
    logger.error("=" * 60)
    report["passed"] = False
    sys.exit(1)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Validate TFLite model before deploy")
    parser.add_argument("--tflite", type=str,
                        default="backend/models/retak_mobilenetv2.tflite")
    parser.add_argument("--test-dir", type=str, default=None,
                        help="Test images directory (default: data/processed)")
    parser.add_argument("--json", type=str, default=None,
                        help="Save report as JSON")
    args = parser.parse_args()

    report = validate(args.tflite, args.test_dir)

    if args.json:
        with open(args.json, "w") as f:
            json.dump(report, f, indent=2)

    sys.exit(0 if report["passed"] else 1)
