"""Retak.id Model Training Script v2.

End-to-end training pipeline:
    1. Load YAML configuration
    2. Set random seeds for reproducibility
    3. Load train/val/test datasets from splits directory
    4. Apply extreme augmentation (in-graph via Keras layers)
    5. Build MobileNetV2 model with progressive fine-tuning
    6. Train with MLflow tracking + TensorBoard + checkpointing + early stopping
    7. Evaluate on test set with per-class metrics
    8. Export TFLite INT8 model + labels.txt
    9. Log all to MLflow (params, metrics, artifacts, model)

Usage:
    make train
    uv run python backend/src/training/train.py
    uv run python backend/src/training/train.py --config backend/config/training.yaml
"""

import os
import sys
import logging
from datetime import datetime
from pathlib import Path

import numpy as np
import tensorflow as tf

LOG_FORMAT = "%(asctime)s - %(levelname)s - %(message)s"
logging.basicConfig(level=logging.INFO, format=LOG_FORMAT)
logger = logging.getLogger(__name__)


def set_seeds(seed: int = 42) -> None:
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
        train_path, image_size=img_size, batch_size=batch_size,
        label_mode="categorical", class_names=class_labels, shuffle=True,
    )
    val_ds = tf.keras.utils.image_dataset_from_directory(
        val_path, image_size=img_size, batch_size=batch_size,
        label_mode="categorical", class_names=class_labels, shuffle=False,
    )
    test_ds = tf.keras.utils.image_dataset_from_directory(
        test_path, image_size=img_size, batch_size=batch_size,
        label_mode="categorical", class_names=class_labels, shuffle=False,
    )

    class_counts = {}
    for cls in class_labels:
        cls_dir = os.path.join(train_path, cls)
        if os.path.isdir(cls_dir):
            class_counts[cls] = len([
                f for f in os.listdir(cls_dir)
                if f.lower().endswith((".jpg", ".jpeg", ".png"))
            ])

    logger.info(f"Training samples: {class_counts}")
    logger.info(f"Train batches: {len(train_ds)}, val: {len(val_ds)}, test: {len(test_ds)}")

    AUTOTUNE = tf.data.AUTOTUNE
    train_ds = train_ds.prefetch(AUTOTUNE)
    val_ds = val_ds.prefetch(AUTOTUNE)
    test_ds = test_ds.prefetch(AUTOTUNE)

    return train_ds, val_ds, test_ds, class_counts


def build_model(config):
    """Build MobileNetV2 with progressive fine-tuning support.

    - If freeze_base=True: freezes all base layers (transfer learning)
    - If freeze_base=False and fine_tune_at is set: unfreezes layers from fine_tune_at onwards
    """
    INPUT_SHAPE = tuple(config.model.input_shape)

    logger.info(f"Building {config.model.base} (weights={config.model.weights})...")

    base_model = tf.keras.applications.MobileNetV2(
        input_shape=INPUT_SHAPE,
        include_top=False,
        weights=config.model.weights,
        alpha=1.0,
    )

    # Fine-tuning logic
    freeze_base = getattr(config.model, "freeze_base", True)
    fine_tune_at = getattr(config.model, "fine_tune_at", None)

    if freeze_base:
        base_model.trainable = False
        logger.info("Base model fully frozen (transfer learning mode)")
    elif fine_tune_at is not None:
        # Freeze all layers first, then unfreeze from fine_tune_at onwards
        base_model.trainable = True
        for layer in base_model.layers[:fine_tune_at]:
            layer.trainable = False
        trainable_count = sum(1 for l in base_model.layers if l.trainable)
        logger.info(
            f"Fine-tuning: layers {fine_tune_at}+ unfrozen "
            f"({trainable_count}/{len(base_model.layers)} layers trainable)"
        )
    else:
        base_model.trainable = True
        logger.info("All base layers unfrozen (full fine-tuning)")

    inputs = tf.keras.Input(shape=INPUT_SHAPE)
    x = tf.keras.applications.mobilenet_v2.preprocess_input(inputs)
    x = base_model(x, training=not freeze_base)
    x = tf.keras.layers.GlobalAveragePooling2D()(x)
    x = tf.keras.layers.Dropout(config.model.dropout)(x)
    outputs = tf.keras.layers.Dense(config.model.num_classes, activation="softmax")(x)

    model = tf.keras.Model(inputs, outputs, name="retak_mobilenetv2")

    lr = config.training.learning_rate
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=lr),
        loss=config.training.loss,
        metrics=["accuracy"],
    )

    total = model.count_params()
    trainable = sum(tf.keras.backend.count_params(w) for w in model.trainable_weights)
    logger.info(f"Params: {total:,} total, {trainable:,} trainable")
    return model


def compute_class_weights(class_counts: dict, class_labels: list) -> dict | None:
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
        "Class weights (ratio %.1f:1): %s",
        ratio,
        {class_labels[i]: f"{w:.2f}" for i, w in weight_dict.items()},
    )
    return weight_dict


def setup_mlflow(config):
    """Initialize MLflow tracking (local filesystem, no server needed)."""
    import mlflow

    mlflow_uri = getattr(config.logging, "mlflow_uri", "backend/logs/mlruns")
    abs_uri = str(Path(mlflow_uri).resolve())
    mlflow.set_tracking_uri(f"file://{abs_uri}")
    mlflow.set_experiment("retak-soil-cracks")

    # Enable TensorFlow autologging
    mlflow.tensorflow.autolog(
        log_models=False,  # we log manually for control
        log_input_examples=False,
    )

    logger.info(f"MLflow tracking: {abs_uri}")
    return mlflow


def train(config, train_ds, val_ds, class_weight=None):
    """Run training with MLflow + full callbacks suite."""
    import mlflow

    model = build_model(config)
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")

    os.makedirs(config.logging.tensorboard_dir, exist_ok=True)
    os.makedirs(config.logging.checkpoint_dir, exist_ok=True)

    checkpoint_path = os.path.join(config.logging.checkpoint_dir, "best.keras")

    callbacks = [
        tf.keras.callbacks.TensorBoard(
            log_dir=os.path.join(config.logging.tensorboard_dir, timestamp),
            histogram_freq=1, write_graph=True,
        ),
        tf.keras.callbacks.ModelCheckpoint(
            filepath=checkpoint_path,
            monitor="val_accuracy", mode="max",
            save_best_only=True, verbose=1,
        ),
        tf.keras.callbacks.EarlyStopping(
            monitor="val_loss",
            patience=config.training.early_stopping_patience,
            restore_best_weights=True, verbose=1,
        ),
        tf.keras.callbacks.ReduceLROnPlateau(
            monitor="val_loss", factor=0.5,
            patience=config.training.reduce_lr_patience,
            min_lr=1e-7, verbose=1,
        ),
    ]

    # Log hyperparams to MLflow
    mlflow.log_params({
        "model_base": config.model.base,
        "dropout": config.model.dropout,
        "freeze_base": getattr(config.model, "freeze_base", True),
        "fine_tune_at": getattr(config.model, "fine_tune_at", None),
        "learning_rate": config.training.learning_rate,
        "batch_size": config.data.batch_size,
        "epochs": config.training.epochs,
        "class_weight": str(getattr(config.training, "class_weight", None)),
        "rotation_range": config.augmentation.rotation_range,
        "zoom_range": config.augmentation.zoom_range,
        "brightness_range": str(config.augmentation.brightness_range),
    })

    logger.info(
        f"Training max {config.training.epochs} epochs "
        f"(early stopping patience={config.training.early_stopping_patience})..."
    )

    history = model.fit(
        train_ds, validation_data=val_ds,
        epochs=config.training.epochs,
        callbacks=callbacks,
        class_weight=class_weight,
        verbose=1,
    )

    best_val = max(history.history.get("val_accuracy", [0]))
    logger.info(f"Training complete. Best val accuracy: {best_val:.4f}")
    return model, history


def evaluate_and_export(config, model, train_ds, test_ds):
    """Evaluate model, export TFLite, log to MLflow."""
    import mlflow
    from backend.src.training.evaluation import evaluate_model, plot_training_history

    eval_dir = os.path.dirname(config.logging.tensorboard_dir)
    eval_results = evaluate_model(
        model, test_ds, config.export.class_labels, output_dir=eval_dir
    )

    # Log metrics to MLflow
    mlflow.log_metrics({
        "test_accuracy": eval_results["accuracy"],
        **{
            f"f1_{cls}": eval_results["classification_report"].get(cls, {}).get("f1-score", 0)
            for cls in config.export.class_labels
        },
        **{
            f"precision_{cls}": eval_results["classification_report"].get(cls, {}).get("precision", 0)
            for cls in config.export.class_labels
        },
        **{
            f"recall_{cls}": eval_results["classification_report"].get(cls, {}).get("recall", 0)
            for cls in config.export.class_labels
        },
    })

    # Log artifacts
    for artifact in ["confusion_matrix.png", "roc_curves.png", "training_history.png"]:
        path = os.path.join(eval_dir, artifact)
        if os.path.exists(path):
            mlflow.log_artifact(path)

    # Export TFLite
    from backend.src.training.export import export_tflite
    tflite_path, labels_path = export_tflite(model, train_ds, config)

    # Log TFLite model as artifact
    mlflow.log_artifact(tflite_path)
    mlflow.log_artifact(labels_path)

    # Log Keras model
    mlflow.keras.log_model(model, "keras_model")

    return eval_results, tflite_path, labels_path


def main(config_path: str = "backend/config/training.yaml", override_path: str | None = None):
    sys.path.insert(0, ".")
    from backend.src.training.config_loader import load_config_with_overrides

    config = load_config_with_overrides(
        base_path="backend/config/training.yaml",
        override_path=override_path,
    )

    # MLflow setup
    mlflow = setup_mlflow(config)

    # Run name includes experiment name
    exp_name = Path(override_path).stem if override_path else "default"
    run_name = f"{exp_name}_{datetime.now().strftime('%Y%m%d_%H%M%S')}"

    with mlflow.start_run(run_name=run_name):
        logger.info(f"Config: {config_path}, override: {override_path or 'none'}")
        logger.info(
            f"Model: {config.model.base}, classes: {config.export.class_labels}, "
            f"LR: {config.training.learning_rate}, dropout: {config.model.dropout}, "
            f"fine_tune_at: {getattr(config.model, 'fine_tune_at', None)}, "
            f"class_weight: {getattr(config.training, 'class_weight', None)}"
        )

        set_seeds(config.data.seed)

        train_ds, val_ds, test_ds, class_counts = load_datasets(config)

        # Class weights
        class_weight = None
        cw_config = getattr(config.training, "class_weight", None)
        if cw_config == "balanced":
            class_weight = compute_class_weights(class_counts, config.export.class_labels)
        elif isinstance(cw_config, dict):
            class_weight = {
                config.export.class_labels.index(k): v
                for k, v in cw_config.items()
            }

        # Augmentation
        from backend.src.training.augment import build_augmentation
        aug = build_augmentation(config.augmentation.to_dict())
        train_ds = train_ds.map(
            lambda x, y: (aug(x, training=True), y),
            num_parallel_calls=tf.data.AUTOTUNE,
        )
        logger.info("Augmentation applied to training set")

        # Train
        model, history = train(config, train_ds, val_ds, class_weight=class_weight)

        # Evaluate + Export
        eval_results, tflite_path, labels_path = evaluate_and_export(
            config, model, train_ds, test_ds
        )

        logger.info("=" * 60)
        logger.info("Pipeline complete!")
        logger.info(f"Model: {tflite_path}")
        logger.info(f"Labels: {labels_path}")
        logger.info(f"Test accuracy: {eval_results['accuracy']:.4f}")
        logger.info(
            f"MLflow: mlflow ui --backend-store-uri file://"
            f"{Path(config.logging.mlflow_uri).resolve()}"
        )
        logger.info("=" * 60)


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="Train Retak.id model")
    parser.add_argument("--config", type=str, default="backend/config/training.yaml")
    parser.add_argument("--override", type=str, default=None,
                        help="Path to experiment override YAML")
    args = parser.parse_args()
    main(args.config, args.override)
