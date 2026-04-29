"""Tests for TFLite export and INT8 quantization."""

import tempfile
import numpy as np
import tensorflow as tf
import os


def _build_dummy_model():
    """Build a tiny model for fast export testing."""
    inputs = tf.keras.Input(shape=(224, 224, 3))
    x = tf.keras.layers.Conv2D(8, 3, activation="relu")(inputs)
    x = tf.keras.layers.GlobalAveragePooling2D()(x)
    outputs = tf.keras.layers.Dense(3, activation="softmax")(x)
    return tf.keras.Model(inputs, outputs)


def _dummy_dataset(n: int = 20):
    """Create a dummy uint8 dataset for calibration."""
    images = np.random.randint(0, 255, (n, 224, 224, 3), dtype=np.uint8).astype(
        np.float32
    )
    labels = np.eye(3)[np.random.randint(0, 3, n)]
    return tf.data.Dataset.from_tensor_slices((images, labels)).batch(4)


def test_tflite_export():
    """Export succeeds and produces valid TFLite file."""
    from backend.src.training.export import export_tflite
    from backend.src.training.config_loader import load_config

    config = load_config("backend/config/training.yaml")
    model = _build_dummy_model()
    ds = _dummy_dataset()

    with tempfile.TemporaryDirectory() as tmpdir:
        config.export.output_dir = tmpdir
        tflite_path, labels_path = export_tflite(model, ds, config)

        assert os.path.exists(tflite_path), f"TFLite not found: {tflite_path}"
        assert os.path.exists(labels_path), f"Labels not found: {labels_path}"

        # Verify TFLite file loads
        interpreter = tf.lite.Interpreter(model_path=tflite_path)
        interpreter.allocate_tensors()
        assert interpreter.get_input_details()[0]["dtype"] == np.uint8


def test_labels_file():
    """labels.txt contains correct class names."""
    from backend.src.training.export import export_tflite
    from backend.src.training.config_loader import load_config

    config = load_config("backend/config/training.yaml")
    model = _build_dummy_model()
    ds = _dummy_dataset()

    with tempfile.TemporaryDirectory() as tmpdir:
        config.export.output_dir = tmpdir
        _, labels_path = export_tflite(model, ds, config)

        with open(labels_path) as f:
            labels = f.read().strip().split("\n")
        assert labels == ["AMAN", "WASPADA", "BAHAYA"]


def test_benchmark():
    """Benchmark runs without error on FP32 and INT8 models."""
    from backend.src.training.export import export_tflite, benchmark_tflite
    from backend.src.training.config_loader import load_config

    config = load_config("backend/config/training.yaml")
    model = _build_dummy_model()
    ds = _dummy_dataset()

    with tempfile.TemporaryDirectory() as tmpdir:
        config.export.output_dir = tmpdir
        tflite_path, _ = export_tflite(model, ds, config)
        results = benchmark_tflite(tflite_path, num_runs=10)

        assert results["mean_latency_ms"] > 0
        assert results["num_runs"] == 10
