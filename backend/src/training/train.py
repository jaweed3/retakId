"""Retak.id Model Training Script.

End-to-end training pipeline:
    1. Load YAML configuration
    2. Set random seeds for reproducibility
    3. Load train/val/test datasets from splits directory
    4. Apply extreme augmentation (in-graph via Keras layers)
    5. Build MobileNetV2 transfer learning model
    6. Train with TensorBoard + checkpointing + early stopping
    7. Evaluate on test set with per-class metrics
    8. Export TFLite INT8 model + labels.txt
    9. Log run metadata to runs.csv

Usage:
    make train
    uv run python backend/src/training/train.py
    uv run python backend/src/training/train.py --config backend/config/training.yaml
"""

import os
import sys
import csv
import logging
from datetime import datetime
from pathlib import Path

import numpy as np
import tensorflow as tf

LOG_FORMAT = "%(asctime)s - %(levelname)s - %(message)s"
logging.basicConfig(level=logging.INFO, format=LOG_FORMAT)
logger = logging.getLogger(__name__)


def set_seeds(seed: int = 42) -> None:
    """Set all random seeds for reproducibility."""
    import random

    random.seed(seed)
    np.random.seed(seed)
    tf.random.set_seed(seed)
    tf.keras.utils.set_random_seed(seed)


def load_datasets(config):
    """Load train/val/test datasets from splits directory."""
    splits_dir = config.data.splits_dir
    img_size = tuple(config.data.img_size)
    batch_size = config.data.batch_size
    class_labels = config.export.class_labels

    train_path = os.path.join(splits_dir, "train")
    val_path = os.path.join(splits_dir, "val")
    test_path = os.path.join(splits_dir, "test")

    for path, name in [(train_path, "train"), (val_path, "val"), (test_path, "test")]:
        if not os.path.isdir(path):
            raise FileNotFoundError(
                f"{name} directory not found: {path}. Run split_dataset.py first."
            )

    logger.info(f"Loading datasets from {splits_dir}...")

    train_ds = tf.keras.utils.image_dataset_from_directory(
        train_path,
        image_size=img_size,
        batch_size=batch_size,
        label_mode="categorical",
        class_names=class_labels,
        shuffle=True,
    )

    val_ds = tf.keras.utils.image_dataset_from_directory(
        val_path,
        image_size=img_size,
        batch_size=batch_size,
        label_mode="categorical",
        class_names=class_labels,
        shuffle=False,
    )

    test_ds = tf.keras.utils.image_dataset_from_directory(
        test_path,
        image_size=img_size,
        batch_size=batch_size,
        label_mode="categorical",
        class_names=class_labels,
        shuffle=False,
    )

    class_counts = {}
    for cls in class_labels:
        cls_dir = os.path.join(train_path, cls)
        if os.path.isdir(cls_dir):
            class_counts[cls] = len(
                [
                    f
                    for f in os.listdir(cls_dir)
                    if f.lower().endswith((".jpg", ".jpeg", ".png"))
                ]
            )

    logger.info(f"Training samples: {class_counts}")
    logger.info(
        f"Train batches: {len(train_ds)}, val: {len(val_ds)}, test: {len(test_ds)}"
    )

    AUTOTUNE = tf.data.AUTOTUNE
    train_ds = train_ds.prefetch(AUTOTUNE)
    val_ds = val_ds.prefetch(AUTOTUNE)
    test_ds = test_ds.prefetch(AUTOTUNE)

    return train_ds, val_ds, test_ds, class_counts


def build_model(config):
    """Build MobileNetV2 transfer learning model."""
    IMG_SIZE = tuple(config.data.img_size)
    NUM_CLASSES = config.model.num_classes
    DROPOUT = config.model.dropout
    LEARNING_RATE = config.training.learning_rate
    INPUT_SHAPE = tuple(config.model.input_shape)

    logger.info(f"Building {config.model.base} (weights={config.model.weights})...")

    base_model = tf.keras.applications.MobileNetV2(
        input_shape=INPUT_SHAPE,
        include_top=False,
        weights=config.model.weights,
        alpha=1.0,
    )

    if config.model.freeze_base:
        base_model.trainable = False
        logger.info("Base model frozen (transfer learning mode)")

    inputs = tf.keras.Input(shape=INPUT_SHAPE)
    x = tf.keras.applications.mobilenet_v2.preprocess_input(inputs)
    x = base_model(x, training=False)
    x = tf.keras.layers.GlobalAveragePooling2D()(x)
    x = tf.keras.layers.Dropout(DROPOUT)(x)
    outputs = tf.keras.layers.Dense(NUM_CLASSES, activation="softmax")(x)

    model = tf.keras.Model(inputs, outputs, name="retak_mobilenetv2")

    optimizer = tf.keras.optimizers.Adam(learning_rate=LEARNING_RATE)
    model.compile(
        optimizer=optimizer,
        loss=config.training.loss,
        metrics=["accuracy"],
    )

    total_params = model.count_params()
    trainable_params = sum(
        [tf.keras.backend.count_params(w) for w in model.trainable_weights]
    )
    logger.info(
        f"Total params: {total_params:,}, Trainable: {trainable_params:,}"
    )
    return model


def compute_class_weights(class_counts: dict, class_labels: list) -> dict | None:
    """Compute class weights for imbalanced training.

    Uses sklearn's "balanced" formula: n_samples / (n_classes * n_samples_per_class)

    Args:
        class_counts: dict mapping class name → count in training set.
        class_labels: ordered list of class names.

    Returns:
        dict mapping class index → weight, or None if counts are balanced (<2:1 ratio).
    """
    from sklearn.utils.class_weight import compute_class_weight as _compute

    counts = [class_counts.get(cls, 0) for cls in class_labels]
    total = sum(counts)
    if total == 0:
        return None

    ratio = max(counts) / max(min(counts), 1)
    if ratio < 2.0:
        logger.info("Class distribution balanced (ratio %.1f:1), no weighting needed", ratio)
        return None

    weights = _compute(
        class_weight="balanced",
        classes=np.array(range(len(class_labels))),
        y=np.concatenate([[i] * c for i, c in enumerate(counts)]),
    )

    weight_dict = {i: float(w) for i, w in enumerate(weights)}
    logger.info(
        "Class weights computed (ratio %.1f:1): %s",
        ratio,
        {class_labels[i]: f"{w:.2f}" for i, w in weight_dict.items()},
    )
    return weight_dict


def train(config, train_ds, val_ds, class_weight=None):
    """Run training with full callbacks suite."""
    model = build_model(config)

    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    os.makedirs(config.logging.tensorboard_dir, exist_ok=True)
    os.makedirs(config.logging.checkpoint_dir, exist_ok=True)

    callbacks = [
        tf.keras.callbacks.TensorBoard(
            log_dir=os.path.join(config.logging.tensorboard_dir, timestamp),
            histogram_freq=1,
            write_graph=True,
        ),
        tf.keras.callbacks.ModelCheckpoint(
            filepath=os.path.join(config.logging.checkpoint_dir, "best.keras"),
            monitor="val_accuracy",
            mode="max",
            save_best_only=True,
            verbose=1,
        ),
        tf.keras.callbacks.EarlyStopping(
            monitor="val_loss",
            patience=config.training.early_stopping_patience,
            restore_best_weights=True,
            verbose=1,
        ),
        tf.keras.callbacks.ReduceLROnPlateau(
            monitor="val_loss",
            factor=0.5,
            patience=config.training.reduce_lr_patience,
            min_lr=1e-7,
            verbose=1,
        ),
        tf.keras.callbacks.CSVLogger(
            filename=os.path.join(
                os.path.dirname(config.logging.checkpoint_dir),
                f"training_log_{timestamp}.csv",
            )
        ),
    ]

    logger.info(
        f"Training for max {config.training.epochs} epochs "
        f"(early stopping patience={config.training.early_stopping_patience})..."
    )

    history = model.fit(
        train_ds,
        validation_data=val_ds,
        epochs=config.training.epochs,
        callbacks=callbacks,
        class_weight=class_weight,
        verbose=1,
    )

    best_val = max(history.history.get("val_accuracy", [0]))
    logger.info(f"Training complete. Best val accuracy: {best_val:.4f}")
    return model, history


def log_run(config, results: dict, tflite_path: str) -> None:
    """Log training run metadata to runs.csv."""
    runs_csv = config.logging.runs_csv
    os.makedirs(os.path.dirname(runs_csv), exist_ok=True)

    row = {
        "timestamp": datetime.now().isoformat(),
        "model": config.model.base,
        "dropout": config.model.dropout,
        "learning_rate": config.training.learning_rate,
        "batch_size": config.data.batch_size,
        "epochs_completed": results.get("epochs_completed", 0),
        "test_accuracy": results.get("test_accuracy", 0),
        "tflite_path": tflite_path,
    }
    for cls in config.export.class_labels:
        cls_data = results.get("per_class", {}).get(cls, {})
        if isinstance(cls_data, dict):
            row[f"f1_{cls}"] = cls_data.get("f1-score", 0)
            row[f"precision_{cls}"] = cls_data.get("precision", 0)
            row[f"recall_{cls}"] = cls_data.get("recall", 0)

    file_exists = os.path.isfile(runs_csv)
    with open(runs_csv, "a", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=row.keys())
        if not file_exists:
            writer.writeheader()
        writer.writerow(row)
    logger.info(f"Run metadata logged to {runs_csv}")


def main(config_path: str = "backend/config/training.yaml"):
    """Full training pipeline entry point."""
    sys.path.insert(0, ".")
    from backend.src.training.config_loader import load_config

    config = load_config(config_path)

    logger.info(f"Config: {config_path}")
    logger.info(f"Model: {config.model.base}, classes: {config.export.class_labels}")
    logger.info(
        f"Img: {config.data.img_size}, batch: {config.data.batch_size}, "
        f"LR: {config.training.learning_rate}, dropout: {config.model.dropout}"
    )

    set_seeds(config.data.seed)
    logger.info(f"Seeds: {config.data.seed}")

    train_ds, val_ds, test_ds, class_counts = load_datasets(config)

    # Compute class weights for imbalanced data
    class_weight = None
    cw_config = getattr(config.training, "class_weight", None)
    if cw_config == "balanced":
        class_weight = compute_class_weights(class_counts, config.export.class_labels)
    elif isinstance(cw_config, dict):
        # Map class names → indices as expected by Keras
        class_weight = {
            config.export.class_labels.index(k): v
            for k, v in cw_config.items()
        }
        logger.info("Using explicit class weights: %s", cw_config)

    from backend.src.training.augment import build_augmentation

    aug = build_augmentation(config.augmentation.to_dict())
    train_ds = train_ds.map(
        lambda x, y: (aug(x, training=True), y),
        num_parallel_calls=tf.data.AUTOTUNE,
    )
    logger.info("Augmentation applied to training set")

    model, history = train(config, train_ds, val_ds, class_weight=class_weight)

    from backend.src.training.evaluation import evaluate_model, plot_training_history

    eval_dir = os.path.dirname(config.logging.tensorboard_dir)
    plot_training_history(history, eval_dir)

    eval_results = evaluate_model(
        model, test_ds, config.export.class_labels, output_dir=eval_dir
    )

    from backend.src.training.export import export_tflite

    tflite_path, labels_path = export_tflite(model, train_ds, config)

    results = {
        "epochs_completed": len(history.history.get("loss", [])),
        "test_accuracy": eval_results["accuracy"],
        "per_class": eval_results["classification_report"],
    }
    log_run(config, results, tflite_path)

    logger.info("=" * 60)
    logger.info("Pipeline complete!")
    logger.info(f"Model: {tflite_path}")
    logger.info(f"Labels: {labels_path}")
    logger.info(f"Test accuracy: {eval_results['accuracy']:.4f}")
    logger.info(f"TensorBoard: tensorboard --logdir {config.logging.tensorboard_dir}")
    logger.info("=" * 60)


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description="Train Retak.id model")
    parser.add_argument(
        "--config",
        type=str,
        default="backend/config/training.yaml",
        help="Path to training config YAML",
    )
    args = parser.parse_args()
    main(args.config)
