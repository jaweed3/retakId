#!/usr/bin/env python3
"""Debug: compare Keras vs TFLite predictions on test images.

Run this on lab PC to verify model predictions are consistent.
If Keras predicts correctly but TFLite is wrong → preprocessing issue.
"""

import os, sys, argparse, logging
import numpy as np
import tensorflow as tf
from pathlib import Path
from PIL import Image

LOG_FORMAT = "%(asctime)s - %(levelname)s - %(message)s"
logging.basicConfig(level=logging.INFO, format=LOG_FORMAT)
logger = logging.getLogger(__name__)


def debug_inference(tflite_path, test_dir, n_samples=5):
    """Compare Keras vs TFLite predictions on real images."""

    # Load TFLite model
    interpreter = tf.lite.Interpreter(model_path=tflite_path)
    interpreter.allocate_tensors()
    input_details = interpreter.get_input_details()[0]
    output_details = interpreter.get_output_details()[0]

    logger.info(f"TFLite input: dtype={input_details['dtype']}, shape={input_details['shape']}")
    logger.info(f"TFLite input quantization: {input_details.get('quantization', 'none')}")
    logger.info(f"TFLite output: dtype={output_details['dtype']}, shape={output_details['shape']}")
    logger.info(f"TFLite output quantization: {output_details.get('quantization', 'none')}")

    # Load test images
    labels = ["AMAN", "WASPADA", "BAHAYA"]
    images = []
    for cls in labels:
        cls_dir = Path(test_dir) / cls
        for f in sorted(cls_dir.glob("*"))[:max(1, n_samples // 3)]:
            if f.suffix.lower() in (".jpg", ".jpeg", ".png"):
                images.append((str(f), cls))

    logger.info(f"\nTesting {len(images)} images...")
    logger.info("-" * 70)

    for img_path, true_label in images:
        # Load and preprocess image
        img = Image.open(img_path).convert("RGB").resize((224, 224))
        img_np = np.array(img, dtype=np.float32)

        # --- Method 1: Raw uint8 (Android-style) ---
        img_uint8 = np.array(img, dtype=np.uint8)
        img_uint8_batch = np.expand_dims(img_uint8, axis=0)

        # --- Method 2: Preprocessed float (Training-style) ---
        img_preprocessed = tf.keras.applications.mobilenet_v2.preprocess_input(
            img_np.copy()
        )
        img_prep_batch = np.expand_dims(img_preprocessed, axis=0)

        # --- TFLite Inference (uint8 input — what Android does) ---
        interpreter.set_tensor(input_details["index"], img_uint8_batch)
        interpreter.invoke()
        tflite_out = interpreter.get_tensor(output_details["index"])
        tflite_out = tflite_out.astype(np.float32)

        # Softmax (TFLite output is raw uint8)
        if output_details["dtype"] == np.uint8:
            tflite_out = tflite_out / 255.0
            tflite_probs = np.exp(tflite_out) / np.sum(np.exp(tflite_out), axis=-1, keepdims=True)
        else:
            tflite_probs = tflite_out / np.sum(tflite_out, axis=-1, keepdims=True)

        tflite_pred = labels[np.argmax(tflite_probs[0])]
        tflite_conf = np.max(tflite_probs[0])

        # --- Report ---
        prob_str = ", ".join(
            f"{labels[i]}: {tflite_probs[0][i]:.3f}" for i in range(3)
        )
        match = "✓" if tflite_pred == true_label else "✗"
        logger.info(
            f"{match} [{true_label}] {Path(img_path).name:30s} "
            f"→ {tflite_pred} ({tflite_conf:.3f})  [{prob_str}]"
        )

    # Summary
    all_same = all(
        np.argmax(tflite_probs, axis=1) == np.argmax(tflite_probs, axis=1)
    )
    # Check if model predicts only one class
    all_preds = []
    for img_path, _ in images:
        img = np.array(Image.open(img_path).convert("RGB").resize((224, 224)), dtype=np.uint8)
        interpreter.set_tensor(input_details["index"], np.expand_dims(img, 0))
        interpreter.invoke()
        out = interpreter.get_tensor(output_details["index"]).astype(np.float32) / 255.0
        probs = np.exp(out) / np.sum(np.exp(out), axis=-1, keepdims=True)
        all_preds.append(labels[np.argmax(probs[0])])

    unique = set(all_preds)
    logger.info(f"\nPredictions made: {unique}")
    if len(unique) == 1:
        logger.error("MODEL PREDICTS ONLY ONE CLASS — preprocessing MISMATCH!")
        logger.error("The model always predicts: " + list(unique)[0])


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Debug TFLite inference")
    parser.add_argument("--tflite", type=str, default="backend/models/retak_mobilenetv2.tflite")
    parser.add_argument("--test-dir", type=str, default="backend/data/processed")
    parser.add_argument("--samples", type=int, default=9)
    args = parser.parse_args()

    debug_inference(args.tflite, args.test_dir, args.samples)
