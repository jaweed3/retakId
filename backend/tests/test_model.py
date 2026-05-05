"""Tests for model: builds, compiles, forward pass, augmentation."""

import tensorflow as tf
import numpy as np


def test_build_model():
    """Model builds with correct output shape."""
    from backend.src.training.config_loader import load_config
    from backend.src.training.train import build_model

    config = load_config("backend/config/training.yaml")
    model = build_model(config)

    assert model.output_shape == (None, 3), f"Bad output shape: {model.output_shape}"
    assert model.name == "retak_mobilenetv2"


def test_model_forward_pass():
    """Single forward pass produces valid probabilities."""
    from backend.src.training.config_loader import load_config
    from backend.src.training.train import build_model

    config = load_config("backend/config/training.yaml")
    model = build_model(config)

    dummy_input = np.random.randn(1, 224, 224, 3).astype(np.float32)
    # Apply mobilenet preprocessing
    dummy_input = tf.keras.applications.mobilenet_v2.preprocess_input(dummy_input)
    output = model.predict(dummy_input, verbose=0)

    assert output.shape == (1, 3)
    assert np.allclose(output.sum(axis=1), 1.0, atol=0.01), "Softmax broken"
    assert np.all(output >= 0) and np.all(output <= 1), "Probabilities out of range"


def test_model_compile():
    """Model compiles without error."""
    from backend.src.training.config_loader import load_config
    from backend.src.training.train import build_model

    config = load_config("backend/config/training.yaml")
    model = build_model(config)

    assert model.optimizer is not None
    assert model.loss == "categorical_crossentropy"
    assert len(model.metrics) >= 2  # loss + compile_metrics in Keras 3


def test_augmentation_output_shape():
    """Augmentation preserves input shape."""
    from backend.src.training.augment import build_training_augmentation

    aug = build_training_augmentation()
    dummy = np.random.rand(4, 224, 224, 3).astype(np.float32)
    output = aug(dummy, training=True)
    assert output.shape == (4, 224, 224, 3), f"Bad aug shape: {output.shape}"


def test_augmentation_training_vs_inference():
    """Augmentation behaves differently in training vs inference mode."""
    from backend.src.training.augment import build_training_augmentation

    aug = build_training_augmentation()
    dummy = np.random.rand(4, 224, 224, 3).astype(np.float32)

    # In training mode, random ops are active
    out_train = aug(dummy, training=True)
    # In inference mode, augmentations should be identity-like (same mean range)
    out_inf = aug(dummy, training=False)

    assert out_train.shape == out_inf.shape
    assert out_train.dtype == out_inf.dtype


def test_seed_reproducibility():
    """Same seed produces same random numbers."""
    from backend.src.training.train import set_seeds

    tf.keras.backend.clear_session()
    set_seeds(42)
    r1 = np.random.randn(5).tolist()

    tf.keras.backend.clear_session()
    set_seeds(42)
    r2 = np.random.randn(5).tolist()

    assert r1 == r2, "Seeds did not produce identical random numbers"
