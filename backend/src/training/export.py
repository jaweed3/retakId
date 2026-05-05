"""TFLite export with INT8 post-training quantization.

Exports trained Keras model to TFLite with:
- INT8 PTQ using representative dataset
- TFLite metadata (labels, input/output specs)
- FP32 vs INT8 accuracy comparison
- Model size validation

Usage:
    uv run python backend/src/training/export.py --model-path backend/models/checkpoints/best.keras --config backend/config/training.yaml
"""

import os
import sys
import json
import logging
import argparse
import time
from pathlib import Path

import numpy as np
import tensorflow as tf

logger = logging.getLogger(__name__)


def export_tflite(
    model: tf.keras.Model,
    train_dataset: tf.data.Dataset,
    config,
) -> tuple[str, str]:
    """Export model to TFLite with INT8 quantization and metadata.

    Args:
        model: Trained Keras model.
        train_dataset: Training dataset for representative calibration.
        config: Configuration namespace from config_loader.

    Returns:
        Tuple of (tflite_path, labels_path).
    """
    output_dir = Path(config.export.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    model_name = config.export.model_name

    # --- 1. FP32 export (baseline) ---
    logger.info("Converting to TFLite (FP32)...")
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    tflite_fp32 = converter.convert()
    fp32_path = output_dir / f"{model_name}_fp32.tflite"
    with open(fp32_path, "wb") as f:
        f.write(tflite_fp32)
    fp32_size_kb = len(tflite_fp32) / 1024
    logger.info(f"FP32 model: {fp32_size_kb:.0f} KB saved to {fp32_path}")

    # --- 2. INT8 PTQ ---
    logger.info("Applying INT8 post-training quantization...")

    def representative_dataset():
        for img, _ in train_dataset.take(config.quantization.representative_samples):
            # Representative dataset needs float32 input
            yield [tf.cast(img, tf.float32)]

    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.representative_dataset = representative_dataset
    converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
    converter.inference_input_type = tf.uint8
    # Keep output as float32 — Android reads FloatArray for softmax

    tflite_int8 = converter.convert()
    int8_path = output_dir / f"{model_name}_int8.tflite"
    with open(int8_path, "wb") as f:
        f.write(tflite_int8)
    int8_size_kb = len(tflite_int8) / 1024
    int8_size_mb = int8_size_kb / 1024
    logger.info(f"INT8 model: {int8_size_kb:.0f} KB ({int8_size_mb:.1f} MB) saved to {int8_path}")

    # --- 3. Compare accuracy ---
    logger.info("Comparing FP32 vs INT8 accuracy on representative samples...")
    _compare_accuracy(model, int8_path, train_dataset, config)

    # --- 4. Export labels ---
    labels_path = output_dir / "labels.txt"
    labels_text = "\n".join(config.export.class_labels)
    with open(labels_path, "w") as f:
        f.write(labels_text)
    logger.info(f"Labels saved to {labels_path} (absolute path: {labels_path.resolve()})")

    # --- 5. Copy INT8 as canonical model ---
    canonical_path = output_dir / f"{model_name}.tflite"
    with open(canonical_path, "wb") as f:
        f.write(tflite_int8)
    logger.info(f"Canonical model: {canonical_path}")

    # --- 6. Size check ---
    target_bytes = config.quantization.target_size_mb * 1024 * 1024
    if len(tflite_int8) > target_bytes:
        logger.warning(
            f"INT8 model size ({int8_size_mb:.1f} MB) exceeds target "
            f"({config.quantization.target_size_mb} MB)"
        )
    else:
        logger.info(f"INT8 model size OK: {int8_size_mb:.1f} MB ≤ {config.quantization.target_size_mb} MB target")

    return str(canonical_path), str(labels_path)


def _compare_accuracy(
    keras_model: tf.keras.Model,
    tflite_path: str,
    dataset: tf.data.Dataset,
    config,
    num_batches: int = 10,
) -> None:
    """Compare FP32 Keras vs INT8 TFLite predictions."""
    interpreter = tf.lite.Interpreter(model_path=str(tflite_path))
    interpreter.allocate_tensors()
    input_index = interpreter.get_input_details()[0]["index"]
    output_index = interpreter.get_output_details()[0]["index"]

    # Get input quantization params
    input_details = interpreter.get_input_details()[0]
    input_scale, input_zero_point = input_details["quantization"]

    keras_preds = []
    tflite_preds = []

    for images, _ in dataset.take(num_batches):
        # Keras prediction
        k_pred = keras_model.predict(images, verbose=0)
        keras_preds.append(np.argmax(k_pred, axis=1))

        # TFLite prediction (with quantization)
        for i in range(len(images)):
            img = images[i].numpy()
            # Quantize input to uint8
            img_uint8 = np.clip(img / input_scale + input_zero_point, 0, 255).astype(np.uint8)
            img_uint8 = np.expand_dims(img_uint8, axis=0)
            interpreter.set_tensor(input_index, img_uint8)
            interpreter.invoke()
            t_pred = interpreter.get_tensor(output_index)
            tflite_preds.append(np.argmax(t_pred[0]))

    keras_preds = np.concatenate(keras_preds)
    tflite_preds = np.array(tflite_preds[: len(keras_preds)])

    agreement = np.mean(keras_preds == tflite_preds)
    logger.info(f"FP32 vs INT8 prediction agreement: {agreement:.2%}")


def benchmark_tflite(tflite_path: str, num_runs: int = 100) -> dict:
    """Benchmark TFLite model inference time.

    Args:
        tflite_path: Path to .tflite file.
        num_runs: Number of inference runs for averaging.

    Returns:
        dict with mean_latency_ms, std_latency_ms, num_runs.
    """
    interpreter = tf.lite.Interpreter(model_path=str(tflite_path))
    interpreter.allocate_tensors()
    input_index = interpreter.get_input_details()[0]["index"]
    output_index = interpreter.get_output_details()[0]["index"]
    input_shape = interpreter.get_input_details()[0]["shape"]

    # Create dummy input
    dummy = np.random.randint(0, 255, input_shape, dtype=np.uint8)

    # Warmup
    for _ in range(5):
        interpreter.set_tensor(input_index, dummy)
        interpreter.invoke()

    # Benchmark
    latencies = []
    for _ in range(num_runs):
        start = time.perf_counter()
        interpreter.set_tensor(input_index, dummy)
        interpreter.invoke()
        _ = interpreter.get_tensor(output_index)
        latencies.append((time.perf_counter() - start) * 1000)  # ms

    results = {
        "mean_latency_ms": float(np.mean(latencies)),
        "std_latency_ms": float(np.std(latencies)),
        "min_latency_ms": float(np.min(latencies)),
        "max_latency_ms": float(np.max(latencies)),
        "num_runs": num_runs,
    }
    logger.info(f"Latency (avg): {results['mean_latency_ms']:.2f} ± {results['std_latency_ms']:.2f} ms")
    return results


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Export TFLite model with INT8 quantization")
    parser.add_argument("--model-path", type=str, required=True, help="Path to trained .keras model")
    parser.add_argument("--config", type=str, default="backend/config/training.yaml", help="Path to config")
    parser.add_argument("--data-dir", type=str, default="backend/data/splits/train", help="Train data for calibration")
    args = parser.parse_args()

    logging.basicConfig(level=logging.INFO, format="%(asctime)s - %(levelname)s - %(message)s")

    sys.path.insert(0, ".")
    from backend.src.training.config_loader import load_config

    config = load_config(args.config)

    model = tf.keras.models.load_model(args.model_path)
    logger.info(f"Loaded model from {args.model_path}")

    # Load a small dataset for calibration
    train_ds = tf.keras.utils.image_dataset_from_directory(
        args.data_dir,
        image_size=tuple(config.data.img_size),
        batch_size=1,
        label_mode="categorical",
        class_names=config.export.class_labels,
    )

    tflite_path, labels_path = export_tflite(model, train_ds, config)
    benchmark_tflite(tflite_path)
