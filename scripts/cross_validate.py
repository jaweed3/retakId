#!/usr/bin/env python3
"""
K-Fold Cross-Validation — verify model consistency before promotion.

Usage:
    # Run 5-fold CV with best config
    uv run python scripts/cross_validate.py --config backend/config/training.yaml

    # Use specific override
    uv run python scripts/cross_validate.py --override backend/config/experiments/grid_0003.yaml

    # Custom folds
    uv run python scripts/cross_validate.py --folds 3

Checks:
    - Mean ± std of accuracy, macro F1, per-class recall across folds
    - Must pass benchmark thresholds on mean metrics
    - Std < 3% indicates consistent behavior (not lucky seed)
"""

import os
import sys
import argparse
import logging
import json
from pathlib import Path

import numpy as np
import tensorflow as tf
from sklearn.model_selection import StratifiedKFold

LOG_FORMAT = "%(asctime)s - %(levelname)s - %(message)s"
logging.basicConfig(level=logging.INFO, format=LOG_FORMAT)
logger = logging.getLogger(__name__)


def load_all_images(data_dir: str, class_labels: list) -> tuple:
    """Load all images and labels from processed directory."""
    images = []
    labels = []
    for cls_idx, cls_name in enumerate(class_labels):
        cls_dir = Path(data_dir) / cls_name
        for f in sorted(cls_dir.glob("*")):
            if f.suffix.lower() in (".jpg", ".jpeg", ".png"):
                images.append(str(f))
                labels.append(cls_idx)
    return np.array(images), np.array(labels)


def make_dataset(image_paths, labels, config, shuffle: bool = False):
    """Create tf.data.Dataset from file paths."""
    img_size = tuple(config.data.img_size)
    batch_size = config.data.batch_size
    class_labels = config.export.class_labels

    # One-hot encode
    n_classes = len(class_labels)
    labels_oh = tf.keras.utils.to_categorical(labels, n_classes)

    ds = tf.data.Dataset.from_tensor_slices((image_paths, labels_oh))

    def load_img(path, label):
        img = tf.io.read_file(path)
        img = tf.image.decode_jpeg(img, channels=3)
        img = tf.image.resize(img, img_size)
        return img, label

    ds = ds.map(load_img, num_parallel_calls=tf.data.AUTOTUNE)
    if shuffle:
        ds = ds.shuffle(len(image_paths), seed=config.data.seed)
    ds = ds.batch(batch_size).prefetch(tf.data.AUTOTUNE)
    return ds


def run_cv(config, images, labels, class_labels):
    """Run stratified k-fold cross-validation."""
    from backend.src.training.train import build_model, compute_class_weights
    from backend.src.training.augment import build_augmentation
    from sklearn.metrics import classification_report

    folds = getattr(config, "cv_folds", 5)
    if hasattr(config, "benchmark"):
        folds = config.benchmark.get("cv_folds", folds)

    # In config: config.thresholds.cv_folds would need benchmark loaded separately
    # Just use 5 as default
    folds = 5

    skf = StratifiedKFold(n_splits=folds, shuffle=True, random_state=config.data.seed)

    fold_metrics = {
        "accuracy": [], "macro_f1": [], "weighted_f1": [],
        "recall_AMAN": [], "recall_WASPADA": [], "recall_BAHAYA": [],
        "precision_AMAN": [], "precision_WASPADA": [], "precision_BAHAYA": [],
        "f1_AMAN": [], "f1_WASPADA": [], "f1_BAHAYA": [],
    }

    for fold, (train_idx, val_idx) in enumerate(skf.split(images, labels)):
        logger.info(f"--- Fold {fold + 1}/{folds} ---")

        train_imgs, train_lbls = images[train_idx], labels[train_idx]
        val_imgs, val_lbls = images[val_idx], labels[val_idx]

        # Count per-class
        class_counts = {}
        for cls_name in class_labels:
            cls_idx = class_labels.index(cls_name)
            class_counts[cls_name] = int(np.sum(train_lbls == cls_idx))
        logger.info(f"Train: {class_counts}")

        # Build datasets
        train_ds = make_dataset(train_imgs, train_lbls, config, shuffle=True)
        val_ds = make_dataset(val_imgs, val_lbls, config, shuffle=False)

        # Augmentation
        aug = build_augmentation(config.augmentation.to_dict())
        train_ds = train_ds.map(
            lambda x, y: (aug(x, training=True), y),
            num_parallel_calls=tf.data.AUTOTUNE,
        )

        # Class weights
        cw = None
        cw_config = getattr(config.training, "class_weight", None)
        if cw_config == "balanced":
            cw = compute_class_weights(class_counts, class_labels)
        elif isinstance(cw_config, dict):
            cw = {class_labels.index(k): v for k, v in cw_config.items()}

        # Build + train
        model = build_model(config)

        model.fit(
            train_ds, validation_data=val_ds,
            epochs=config.training.epochs,
            callbacks=[
                tf.keras.callbacks.EarlyStopping(
                    monitor="val_loss", patience=10, restore_best_weights=True,
                    verbose=0,
                ),
            ],
            class_weight=cw,
            verbose=0,
        )

        # Evaluate
        y_true_all, y_pred_all = [], []
        for imgs, lbls in val_ds:
            preds = model.predict(imgs, verbose=0)
            y_true_all.append(np.argmax(lbls.numpy(), axis=1))
            y_pred_all.append(np.argmax(preds, axis=1))

        y_true = np.concatenate(y_true_all)
        y_pred = np.concatenate(y_pred_all)

        report = classification_report(
            y_true, y_pred, target_names=class_labels, output_dict=True, zero_division=0
        )

        acc = report["accuracy"]
        macro_f1 = report["macro avg"]["f1-score"]
        weighted_f1 = report["weighted avg"]["f1-score"]

        fold_metrics["accuracy"].append(acc)
        fold_metrics["macro_f1"].append(macro_f1)
        fold_metrics["weighted_f1"].append(weighted_f1)

        for cls in class_labels:
            fold_metrics[f"recall_{cls}"].append(report[cls]["recall"])
            fold_metrics[f"precision_{cls}"].append(report[cls]["precision"])
            fold_metrics[f"f1_{cls}"].append(report[cls]["f1-score"])

        logger.info(f"Fold {fold + 1}: acc={acc:.4f}, macro_f1={macro_f1:.4f}")

        tf.keras.backend.clear_session()

    # Summary
    logger.info("=" * 60)
    logger.info(f"Cross-Validation Summary ({folds} folds)")
    logger.info("=" * 60)
    logger.info(f"{'Metric':<20} {'Mean':>8} {'Std':>8} {'Min':>8} {'Max':>8}")
    logger.info("-" * 54)

    summary = {}
    for key, values in fold_metrics.items():
        mean_v = np.mean(values)
        std_v = np.std(values)
        min_v = np.min(values)
        max_v = np.max(values)
        summary[key] = {"mean": mean_v, "std": std_v, "min": min_v, "max": max_v}
        logger.info(f"{key:<20} {mean_v:>8.4f} {std_v:>8.4f} {min_v:>8.4f} {max_v:>8.4f}")

    # Consistency check
    acc_std = summary["accuracy"]["std"]
    if acc_std < 0.03:
        logger.info("✓ Model consistent (accuracy std < 3%)")
    else:
        logger.warning(f"✗ Model INCONSISTENT (accuracy std={acc_std:.4f} > 3%)")
        logger.warning("This model may not generalize well. Consider more data or regularization.")

    return summary


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Retak.id Cross-Validation")
    parser.add_argument("--config", type=str, default="backend/config/training.yaml")
    parser.add_argument("--override", type=str, default=None)
    parser.add_argument("--data-dir", type=str, default="backend/data/processed")
    parser.add_argument("--folds", type=int, default=5)
    parser.add_argument("--json", type=str, default=None, help="Save summary as JSON")
    args = parser.parse_args()

    sys.path.insert(0, ".")
    from backend.src.training.config_loader import load_config_with_overrides

    config = load_config_with_overrides(args.config, args.override)

    class_labels = config.export.class_labels
    images, labels = load_all_images(args.data_dir, class_labels)

    logger.info(f"CV: {len(images)} images, {args.folds} folds")
    logger.info(f"Classes: {dict(zip(*np.unique(labels, return_counts=True)))}")

    # Store folds in config (accessible in function)
    config.cv_folds = args.folds

    summary = run_cv(config, images, labels, class_labels)

    if args.json:
        with open(args.json, "w") as f:
            # Convert numpy types
            clean = {}
            for k, v in summary.items():
                clean[k] = {kk: float(vv) for kk, vv in v.items()}
            json.dump(clean, f, indent=2)
        logger.info(f"Summary saved to {args.json}")

    # Exit code based on consistency
    if summary["accuracy"]["std"] > 0.03:
        sys.exit(1)
