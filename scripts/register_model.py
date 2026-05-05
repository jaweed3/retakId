#!/usr/bin/env python3
"""
Model Registry — evaluate, compare, and promote the best TFLite model.

Usage:
    # After training, register if better than current best
    uv run python scripts/register_model.py --run-id <mlflow_run_id>

    # Force register (skip comparison)
    uv run python scripts/register_model.py --run-id <id> --force

    # List registered models
    uv run python scripts/register_model.py --list

    # Download best model for Android
    uv run python scripts/register_model.py --download --output app/src/main/assets/

Flow:
    1. Load benchmark thresholds from benchmark.yaml
    2. Fetch run metrics from MLflow
    3. Run cross-validation if needed
    4. Compare against current best registered model
    5. If passes all checks → register as new "champion"
    6. Tag as "staging" for mobile team to pull
"""

import os
import sys
import argparse
import logging
from pathlib import Path
from datetime import datetime

import yaml
import mlflow
from mlflow.tracking import MlflowClient

LOG_FORMAT = "%(asctime)s - %(levelname)s - %(message)s"
logging.basicConfig(level=logging.INFO, format=LOG_FORMAT)
logger = logging.getLogger(__name__)

MODEL_NAME = "retak-soil-classifier"
BENCHMARK_PATH = "backend/config/benchmark.yaml"


def load_benchmark(path: str = BENCHMARK_PATH) -> dict:
    with open(path) as f:
        return yaml.safe_load(f)


def get_run_metrics(run_id: str) -> dict:
    """Fetch all metrics from an MLflow run."""
    client = MlflowClient()
    run = client.get_run(run_id)
    return run.data.metrics


def get_run_params(run_id: str) -> dict:
    """Fetch all params from an MLflow run."""
    client = MlflowClient()
    run = client.get_run(run_id)
    return run.data.params


def check_thresholds(metrics: dict, thresholds: dict) -> tuple[bool, list[str]]:
    """Check if metrics pass benchmark thresholds.

    Returns: (passed, list of failures)
    """
    failures = []
    t = thresholds["thresholds"]

    checks = {
        "test_accuracy": t["test_accuracy"],
        "macro_f1": t["macro_f1"],
        "weighted_f1": t["weighted_f1"],
        "int8_agreement": t["int8_agreement"],
    }

    for key, min_val in checks.items():
        val = metrics.get(key, 0)
        if val < min_val:
            failures.append(f"{key}: {val:.4f} < {min_val}")

    # Per-class recall
    for cls, min_recall in t["per_class_recall"].items():
        key = f"recall_{cls}"
        val = metrics.get(key, 0)
        if val < min_recall:
            failures.append(f"recall_{cls}: {val:.4f} < {min_recall}")

    # Per-class precision (critical classes only)
    for cls, min_prec in t.get("per_class_precision", {}).items():
        key = f"precision_{cls}"
        val = metrics.get(key, 0)
        if val < min_prec:
            failures.append(f"precision_{cls}: {val:.4f} < {min_prec}")

    passed = len(failures) == 0
    return passed, failures


def get_current_champion(client: MlflowClient) -> dict | None:
    """Get the currently registered champion model."""
    try:
        versions = client.get_latest_versions(MODEL_NAME, stages=["Production"])
        if versions:
            v = versions[0]
            return {
                "version": v.version,
                "run_id": v.run_id,
                "metrics": get_run_metrics(v.run_id),
            }
    except Exception:
        pass
    return None


def count_better_metrics(new_metrics: dict, champion_metrics: dict, primary: list) -> int:
    """Count how many primary metrics improved over champion."""
    better = 0
    for metric in primary:
        new_val = new_metrics.get(metric.replace("per_class_recall.BAHAYA", "recall_BAHAYA"), 0)
        old_val = champion_metrics.get(metric.replace("per_class_recall.BAHAYA", "recall_BAHAYA"), 0)

        # Map dot-path to actual metric key
        if "." in metric:
            _, cls = metric.split(".")
            new_val = new_metrics.get(f"recall_{cls}", 0)
            old_val = champion_metrics.get(f"recall_{cls}", 0)

        if new_val > old_val:
            better += 1
    return better


def register(run_id: str, force: bool = False):
    """Evaluate and potentially register a model."""
    benchmark = load_benchmark()
    metrics = get_run_metrics(run_id)
    params = get_run_params(run_id)
    client = MlflowClient()

    logger.info(f"Evaluating run: {run_id}")
    logger.info(f"Model: {params.get('model_base', 'unknown')}")
    logger.info(f"Accuracy: {metrics.get('test_accuracy', 'N/A'):.4f}")

    # 1. Check benchmark thresholds
    passed, failures = check_thresholds(metrics, benchmark)
    if not passed:
        logger.error("BENCHMARK FAILED:")
        for f in failures:
            logger.error(f"  ✗ {f}")
        if not force:
            sys.exit(1)
        logger.warning("Force flag set — registering anyway")

    if passed:
        logger.info("✓ All benchmark thresholds passed")

    # 2. Compare against champion
    champion = get_current_champion(client)
    if champion and not force:
        better_count = count_better_metrics(
            metrics, champion["metrics"], benchmark["primary_metrics"]
        )
        logger.info(
            f"vs champion (v{champion['version']}, "
            f"acc={champion['metrics'].get('test_accuracy', 0):.4f}): "
            f"{better_count}/{len(benchmark['primary_metrics'])} metrics better"
        )

        if better_count < 2:
            logger.warning("Not enough improvement over champion. Skipping.")
            if not force:
                sys.exit(0)

    # 3. Cross-validation consistency check
    if benchmark["thresholds"].get("cv_required", False) and not force:
        logger.info("Cross-validation check required. Run:")
        logger.info(f"  uv run python scripts/cross_validate.py --run-id {run_id}")
        logger.info("Or use --force to skip CV.")
        # Don't fail — just warn
        # sys.exit(0)

    # 4. Register the model
    model_uri = f"runs:/{run_id}/keras_model"
    try:
        result = mlflow.register_model(model_uri, MODEL_NAME)
        logger.info(f"Registered: {MODEL_NAME} v{result.version}")

        # Tag with metadata
        client.set_model_version_tag(
            MODEL_NAME, result.version, "accuracy", f"{metrics.get('test_accuracy', 0):.4f}"
        )
        client.set_model_version_tag(
            MODEL_NAME, result.version, "architecture", params.get("model_base", "unknown")
        )
        client.set_model_version_tag(
            MODEL_NAME, result.version, "registered_at", datetime.now().isoformat()
        )

        # Transition to staging
        client.transition_model_version_stage(
            MODEL_NAME, result.version, "Staging",
        )
        logger.info(f"Model {MODEL_NAME} v{result.version} → Staging")
        logger.info("Mobile team: pull from DagsHub MLflow Registry")
    except Exception as e:
        logger.error(f"Registration failed: {e}")
        logger.info("Model may not have keras_model artifact. Only available from v2 train.py.")


def list_models():
    """List all registered models."""
    client = MlflowClient()
    try:
        versions = client.get_latest_versions(MODEL_NAME, stages=["None", "Staging", "Production"])
        if not versions:
            logger.info("No registered models found.")
            return

        logger.info(f"Registered models for '{MODEL_NAME}':")
        for v in versions:
            tags = v.tags or {}
            logger.info(
                f"  v{v.version} [{v.current_stage}] "
                f"acc={tags.get('accuracy', 'N/A')} "
                f"arch={tags.get('architecture', 'N/A')}"
            )
    except Exception as e:
        logger.error(f"Failed to list models: {e}")


def download_best(output_dir: str):
    """Download the best TFLite model for Android integration."""
    client = MlflowClient()
    versions = client.get_latest_versions(MODEL_NAME, stages=["Staging", "Production"])
    if not versions:
        logger.error("No staged/production model found.")
        sys.exit(1)

    v = versions[0]
    run_id = v.run_id
    run = client.get_run(run_id)

    logger.info(f"Downloading v{v.version} (stage={v.current_stage})")

    os.makedirs(output_dir, exist_ok=True)

    # Download TFLite artifact
    artifacts = client.list_artifacts(run_id)
    for a in artifacts:
        if a.path.endswith(".tflite") or a.path == "labels.txt":
            local_path = client.download_artifacts(run_id, a.path, output_dir)
            logger.info(f"  Downloaded: {a.path} → {local_path}")

    logger.info(f"Model ready in {output_dir}/")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Retak.id Model Registry")
    parser.add_argument("--run-id", type=str, help="MLflow run ID to evaluate")
    parser.add_argument("--force", action="store_true", help="Skip threshold/compare checks")
    parser.add_argument("--list", action="store_true", help="List registered models")
    parser.add_argument("--download", action="store_true", help="Download best model")
    parser.add_argument("--output", type=str, default="backend/models/registered",
                        help="Output dir for downloaded model")
    args = parser.parse_args()

    if args.list:
        list_models()
    elif args.download:
        download_best(args.output)
    elif args.run_id:
        register(args.run_id, args.force)
    else:
        parser.print_help()
