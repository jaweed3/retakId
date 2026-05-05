"""Model evaluation utilities.

Computes per-class metrics, confusion matrix, and generates plots.

Usage:
    from backend.src.training.evaluation import evaluate_model, plot_training_history
"""

import os
import logging
from pathlib import Path

import numpy as np
import tensorflow as tf
from sklearn.metrics import (
    classification_report,
    confusion_matrix,
    roc_curve,
    auc,
)
import matplotlib

matplotlib.use("Agg")  # non-interactive backend
import matplotlib.pyplot as plt

logger = logging.getLogger(__name__)


def evaluate_model(
    model: tf.keras.Model,
    test_dataset: tf.data.Dataset,
    class_labels: list[str],
    output_dir: str | None = None,
) -> dict:
    """Evaluate model on test set with full metrics.

    Args:
        model: Trained Keras model.
        test_dataset: Test tf.data.Dataset (images, one-hot labels).
        class_labels: List of class name strings.
        output_dir: If provided, save plots to this directory.

    Returns:
        dict with accuracy, per-class metrics, confusion matrix.
    """
    # Collect predictions
    y_true_all = []
    y_pred_all = []
    y_score_all = []

    for images, labels in test_dataset:
        preds = model.predict(images, verbose=0)
        y_true_all.append(labels.numpy())
        y_pred_all.append(np.argmax(preds, axis=1))
        y_score_all.append(preds)

    y_true = np.argmax(np.concatenate(y_true_all), axis=1)
    y_pred = np.concatenate(y_pred_all)
    y_score = np.concatenate(y_score_all)

    # Metrics
    accuracy = float(np.mean(y_true == y_pred))
    report = classification_report(
        y_true, y_pred, target_names=class_labels, output_dict=True, zero_division=0
    )
    cm = confusion_matrix(y_true, y_pred)

    results = {
        "accuracy": accuracy,
        "classification_report": report,
        "confusion_matrix": cm.tolist(),
    }

    logger.info(f"Test accuracy: {accuracy:.4f}")
    logger.info("\n" + classification_report(
        y_true, y_pred, target_names=class_labels, zero_division=0
    ))

    if output_dir:
        os.makedirs(output_dir, exist_ok=True)
        _plot_confusion_matrix(cm, class_labels, output_dir)
        _plot_roc_curves(y_true, y_score, class_labels, output_dir)

    return results


def _plot_confusion_matrix(
    cm: np.ndarray, class_labels: list[str], output_dir: str
) -> None:
    """Save confusion matrix heatmap."""
    fig, ax = plt.subplots(figsize=(6, 5))
    im = ax.imshow(cm, interpolation="nearest", cmap="Blues")
    ax.figure.colorbar(im, ax=ax)

    ax.set(
        xticks=np.arange(len(class_labels)),
        yticks=np.arange(len(class_labels)),
        xticklabels=class_labels,
        yticklabels=class_labels,
        ylabel="True label",
        xlabel="Predicted label",
    )
    ax.set_title("Confusion Matrix")

    # Annotate cells
    thresh = cm.max() / 2
    for i in range(len(class_labels)):
        for j in range(len(class_labels)):
            ax.text(
                j,
                i,
                str(cm[i, j]),
                ha="center",
                va="center",
                color="white" if cm[i, j] > thresh else "black",
                fontsize=12,
            )

    fig.tight_layout()
    path = os.path.join(output_dir, "confusion_matrix.png")
    fig.savefig(path, dpi=150)
    plt.close(fig)
    logger.info(f"Confusion matrix saved to {path}")


def _plot_roc_curves(
    y_true: np.ndarray,
    y_score: np.ndarray,
    class_labels: list[str],
    output_dir: str,
) -> None:
    """Save one-vs-rest ROC curves."""
    n_classes = len(class_labels)
    y_true_onehot = np.eye(n_classes)[y_true]

    fig, ax = plt.subplots(figsize=(6, 5))

    for i in range(n_classes):
        fpr, tpr, _ = roc_curve(y_true_onehot[:, i], y_score[:, i])
        roc_auc = auc(fpr, tpr)
        ax.plot(fpr, tpr, label=f"{class_labels[i]} (AUC={roc_auc:.2f})")

    ax.plot([0, 1], [0, 1], "k--", alpha=0.3)
    ax.set(
        xlim=[0.0, 1.0],
        ylim=[0.0, 1.05],
        xlabel="False Positive Rate",
        ylabel="True Positive Rate",
        title="ROC Curves (One-vs-Rest)",
    )
    ax.legend(loc="lower right")
    fig.tight_layout()

    path = os.path.join(output_dir, "roc_curves.png")
    fig.savefig(path, dpi=150)
    plt.close(fig)
    logger.info(f"ROC curves saved to {path}")


def plot_training_history(
    history: tf.keras.callbacks.History, output_dir: str | None = None
) -> None:
    """Plot and save training/validation accuracy and loss curves.

    Args:
        history: Keras History object from model.fit().
        output_dir: Directory to save plots.
    """
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(12, 4))

    # Accuracy
    ax1.plot(history.history.get("accuracy", []), label="train")
    if "val_accuracy" in history.history:
        ax1.plot(history.history["val_accuracy"], label="val")
    ax1.set(title="Accuracy", xlabel="Epoch", ylabel="Accuracy")
    ax1.legend()
    ax1.grid(True, alpha=0.3)

    # Loss
    ax2.plot(history.history.get("loss", []), label="train")
    if "val_loss" in history.history:
        ax2.plot(history.history["val_loss"], label="val")
    ax2.set(title="Loss", xlabel="Epoch", ylabel="Loss")
    ax2.legend()
    ax2.grid(True, alpha=0.3)

    fig.tight_layout()

    if output_dir:
        os.makedirs(output_dir, exist_ok=True)
        path = os.path.join(output_dir, "training_history.png")
        fig.savefig(path, dpi=150)
        logger.info(f"Training history saved to {path}")

    plt.close(fig)
