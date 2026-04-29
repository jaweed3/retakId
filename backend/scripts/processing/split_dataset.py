"""Split dataset into train/val/test with stratification.

Usage:
    uv run python backend/scripts/processing/split_dataset.py
    uv run python backend/scripts/processing/split_dataset.py --data-dir backend/data/processed --output-dir backend/data/splits
"""

import os
import shutil
import argparse
import logging
import random
from pathlib import Path

LOG_FORMAT = "%(asctime)s - %(levelname)s - %(message)s"
logging.basicConfig(level=logging.INFO, format=LOG_FORMAT)
logger = logging.getLogger(__name__)


def split_dataset(
    data_dir: str,
    output_dir: str,
    train_ratio: float = 0.7,
    val_ratio: float = 0.15,
    seed: int = 42,
    use_symlinks: bool = False,
) -> dict:
    """Stratified train/val/test split.

    Copies (or symlinks) images from class subdirectories into:
        output_dir/train/<class>/
        output_dir/val/<class>/
        output_dir/test/<class>/
    """
    data_path = Path(data_dir)
    output_path = Path(output_dir)

    if not data_path.exists():
        logger.error(f"Source directory not found: {data_dir}")
        raise SystemExit(1)

    class_dirs = sorted(
        [d for d in data_path.iterdir() if d.is_dir() and not d.name.startswith(".")]
    )
    if not class_dirs:
        logger.error(f"No class directories found in {data_dir}")
        raise SystemExit(1)

    random.seed(seed)
    logger.info(
        f"Split ratios: train={train_ratio:.0%}, val={val_ratio:.0%}, "
        f"test={1 - train_ratio - val_ratio:.0%}"
    )
    logger.info(f"Classes: {[d.name for d in class_dirs]}")

    # Clean output directory
    if output_path.exists():
        shutil.rmtree(output_path)

    stats = {}

    for class_dir in class_dirs:
        class_name = class_dir.name
        images = sorted(
            [
                f
                for f in class_dir.glob("*")
                if f.suffix.lower()
                in (".jpg", ".jpeg", ".png", ".bmp", ".gif", ".webp")
            ]
        )
        random.shuffle(images)

        n_total = len(images)
        n_train = max(1, int(n_total * train_ratio))
        n_val = max(1, int(n_total * val_ratio))
        # Ensure test set gets at least 1 sample
        n_val = min(n_val, n_total - n_train - 1)
        n_test = n_total - n_train - n_val

        splits = {
            "train": images[:n_train],
            "val": images[n_train : n_train + n_val],
            "test": images[n_train + n_val :],
        }

        class_stats = {"total": n_total, "train": n_train, "val": n_val, "test": n_test}
        stats[class_name] = class_stats

        for split_name, split_images in splits.items():
            split_class_dir = output_path / split_name / class_name
            split_class_dir.mkdir(parents=True, exist_ok=True)

            for img_path in split_images:
                dest = split_class_dir / img_path.name
                if use_symlinks:
                    if dest.exists():
                        dest.unlink()
                    dest.symlink_to(img_path.resolve())
                else:
                    shutil.copy2(img_path, dest)

        logger.info(
            f"  {class_name}: {n_total} total → "
            f"train={n_train}, val={n_val}, test={n_test}"
        )

    total = sum(s["total"] for s in stats.values())
    train_total = sum(s["train"] for s in stats.values())
    val_total = sum(s["val"] for s in stats.values())
    test_total = sum(s["test"] for s in stats.values())
    logger.info(
        f"Total: {total} → train={train_total}, val={val_total}, test={test_total}"
    )
    logger.info(f"Output written to: {output_dir}")

    return stats


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Stratified dataset split")
    parser.add_argument(
        "--data-dir",
        type=str,
        default="backend/data/processed",
        help="Path to processed (annotated) dataset",
    )
    parser.add_argument(
        "--output-dir",
        type=str,
        default="backend/data/splits",
        help="Output directory for splits",
    )
    parser.add_argument(
        "--train-ratio",
        type=float,
        default=0.7,
        help="Training split ratio (default: 0.7)",
    )
    parser.add_argument(
        "--val-ratio",
        type=float,
        default=0.15,
        help="Validation split ratio (default: 0.15)",
    )
    parser.add_argument(
        "--seed",
        type=int,
        default=42,
        help="Random seed for reproducibility",
    )
    parser.add_argument(
        "--symlinks",
        action="store_true",
        help="Use symlinks instead of copying files",
    )
    args = parser.parse_args()

    split_dataset(
        data_dir=args.data_dir,
        output_dir=args.output_dir,
        train_ratio=args.train_ratio,
        val_ratio=args.val_ratio,
        seed=args.seed,
        use_symlinks=args.symlinks,
    )
