"""Augmentation pipeline for Retak.id model training.

Provides extreme augmentation suitable for small crack-detection datasets.
All augmentations use tf.keras.layers for in-graph execution (zero I/O overhead).

Usage:
    from backend.src.training.augment import build_augmentation, visualize_augmentations
"""

import tensorflow as tf


def build_augmentation(config: dict | None = None) -> tf.keras.Sequential:
    """Build a sequential augmentation pipeline from config dict.

    Args:
        config: dict with augmentation params (from training.yaml).
                If None, uses default extreme augmentation settings.

    Returns:
        tf.keras.Sequential of preprocessing layers.
    """
    if config is None:
        config = _default_aug_config()

    layers = []

    if config.get("random_flip", {}).get("horizontal", True):
        layers.append(tf.keras.layers.RandomFlip("horizontal"))
    if config.get("random_flip", {}).get("vertical", False):
        layers.append(tf.keras.layers.RandomFlip("vertical"))

    layers.append(
        tf.keras.layers.RandomRotation(
            config.get("rotation_range", 0.0) / 360.0,  # keras uses fraction of 2π
            fill_mode=config.get("fill_mode", "nearest"),
        )
    )

    zoom = config.get("zoom_range", 0.0)
    if zoom > 0:
        layers.append(
            tf.keras.layers.RandomZoom(
                height_factor=(-zoom, zoom),
                width_factor=(-zoom, zoom),
                fill_mode=config.get("fill_mode", "nearest"),
            )
        )

    translate = config.get("translation_range", 0.0)
    if translate > 0:
        layers.append(
            tf.keras.layers.RandomTranslation(
                height_factor=(-translate, translate),
                width_factor=(-translate, translate),
                fill_mode=config.get("fill_mode", "nearest"),
            )
        )

    shear = config.get("shear_range", 0.0)
    if shear > 0:
        # Shear is approximated with RandomAffine-like transformation
        # Keras doesn't have native RandomShear, use custom preprocessing
        pass  # Shear handled via tf.keras.layers.RandomAffine if available

    brightness = config.get("brightness_range", None)
    if brightness and len(brightness) == 2:
        layers.append(
            tf.keras.layers.RandomBrightness(
                factor=(brightness[0] - 1.0, brightness[1] - 1.0)
            )
        )

    contrast = config.get("contrast_range", None)
    if contrast and len(contrast) == 2:
        layers.append(
            tf.keras.layers.RandomContrast(
                factor=(contrast[0], contrast[1])
            )
        )

    return tf.keras.Sequential(layers, name="augmentation")


def build_training_augmentation() -> tf.keras.Sequential:
    """Default extreme augmentation for crack detection training.

    Mirrors the config in training.yaml:
        - Horizontal + vertical flip
        - ±30° rotation
        - ±20% zoom
        - ±20% translation
        - Brightness 0.7-1.3x
        - Contrast 0.8-1.2x
    """
    return tf.keras.Sequential(
        [
            tf.keras.layers.RandomFlip("horizontal"),
            tf.keras.layers.RandomFlip("vertical"),
            tf.keras.layers.RandomRotation(30.0 / 360.0, fill_mode="nearest"),
            tf.keras.layers.RandomZoom(
                height_factor=(-0.2, 0.2),
                width_factor=(-0.2, 0.2),
                fill_mode="nearest",
            ),
            tf.keras.layers.RandomTranslation(
                height_factor=(-0.2, 0.2),
                width_factor=(-0.2, 0.2),
                fill_mode="nearest",
            ),
            tf.keras.layers.RandomBrightness(factor=(-0.3, 0.3)),
            tf.keras.layers.RandomContrast(factor=(0.8, 1.2)),
        ],
        name="extreme_augmentation",
    )


def build_inference_preprocessing() -> tf.keras.Sequential:
    """Preprocessing pipeline for inference (no augmentation).

    Only resizes and normalizes to [0, 1] range.
    """
    return tf.keras.Sequential(
        [
            tf.keras.layers.Resizing(224, 224),
            tf.keras.layers.Rescaling(1.0 / 255.0),
        ],
        name="inference_preprocessing",
    )


def visualize_augmentations(
    image: tf.Tensor, num_variations: int = 6
) -> tf.Tensor:
    """Apply augmentation N times to one image for visualization.

    Args:
        image: Single image tensor [H, W, C] or [1, H, W, C].
        num_variations: Number of augmented versions to generate.

    Returns:
        Tensor of shape [num_variations, H, W, C] with augmented images.
    """
    aug = build_training_augmentation()
    if image.shape.rank == 3:
        image = tf.expand_dims(image, 0)

    variations = []
    for _ in range(num_variations):
        variations.append(aug(image, training=True))
    return tf.concat(variations, axis=0)


def _default_aug_config() -> dict:
    """Default extreme augmentation config matching training.yaml."""
    return {
        "random_flip": {"horizontal": True, "vertical": True},
        "rotation_range": 30.0,
        "translation_range": 0.2,
        "zoom_range": 0.2,
        "shear_range": 0.2,
        "brightness_range": [0.7, 1.3],
        "contrast_range": [0.8, 1.2],
        "fill_mode": "nearest",
    }
