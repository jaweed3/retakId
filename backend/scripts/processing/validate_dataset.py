"""Validate all images in the dataset.

Usage:
    uv run python backend/scripts/processing/validate_dataset.py
    uv run python backend/scripts/processing/validate_dataset.py --data-dir backend/data/processed
"""

import os
import sys
import argparse
import logging
from pathlib import Path

from PIL import Image, UnidentifiedImageError

LOG_FORMAT = "%(asctime)s - %(levelname)s - %(message)s"
logging.basicConfig(level=logging.INFO, format=LOG_FORMAT)
logger = logging.getLogger(__name__)

MIN_DIM = 100  # reject images below 100px in either dimension
MAX_FILE_SIZE_MB = 20  # reject absurdly large files


def validate_directory(data_dir: str) -> dict:
    """Scan class subdirectories and validate every image.

    Returns:
        dict with per-class stats and list of corrupt/invalid files.
    """
    data_path = Path(data_dir)
    if not data_path.exists():
        logger.error(f"Directory not found: {data_dir}")
        sys.exit(1)

    class_dirs = sorted(
        [d for d in data_path.iterdir() if d.is_dir() and not d.name.startswith(".")]
    )
    if not class_dirs:
        logger.error(f"No class subdirectories found in {data_dir}")
        logger.error(
            "Expected structure: data/processed/AMAN/, data/processed/WASPADA/, etc."
        )
        sys.exit(1)

    logger.info(f"Found {len(class_dirs)} classes: {[d.name for d in class_dirs]}")

    stats: dict = {
        "total": 0,
        "valid": 0,
        "invalid": 0,
        "per_class": {},
        "invalid_files": [],
    }

    for class_dir in class_dirs:
        class_name = class_dir.name
        class_stats = {"total": 0, "valid": 0, "invalid": 0}
        images = sorted(class_dir.glob("*"))
        # Filter to common image extensions
        images = [
            f
            for f in images
            if f.suffix.lower() in (".jpg", ".jpeg", ".png", ".bmp", ".gif", ".webp")
        ]
        class_stats["total"] = len(images)

        for img_path in images:
            try:
                file_size_mb = img_path.stat().st_size / (1024 * 1024)
                if file_size_mb > MAX_FILE_SIZE_MB:
                    class_stats["invalid"] += 1
                    stats["invalid_files"].append(
                        {"path": str(img_path), "class": class_name, "reason": "too_large"}
                    )
                    continue

                with Image.open(img_path) as img:
                    img.verify()  # verify file integrity
                # Re-open after verify (verify leaves file in bad state)
                with Image.open(img_path) as img:
                    w, h = img.size
                    if w < MIN_DIM or h < MIN_DIM:
                        class_stats["invalid"] += 1
                        stats["invalid_files"].append(
                            {
                                "path": str(img_path),
                                "class": class_name,
                                "reason": f"too_small ({w}x{h})",
                            }
                        )
                        continue
                    class_stats["valid"] += 1

            except (UnidentifiedImageError, OSError, IOError) as e:
                class_stats["invalid"] += 1
                stats["invalid_files"].append(
                    {"path": str(img_path), "class": class_name, "reason": str(e)}
                )

        stats["per_class"][class_name] = class_stats
        stats["total"] += class_stats["total"]
        stats["valid"] += class_stats["valid"]
        stats["invalid"] += class_stats["invalid"]

        logger.info(
            f"  {class_name}: {class_stats['valid']}/{class_stats['total']} valid"
            + (
                f" ({class_stats['invalid']} invalid)"
                if class_stats["invalid"]
                else ""
            )
        )

    logger.info(
        f"Overall: {stats['valid']}/{stats['total']} valid"
        + (
            f" ({stats['invalid']} invalid)"
            if stats["invalid"]
            else ""
        )
    )

    if stats["invalid_files"]:
        logger.warning(f"Found {len(stats['invalid_files'])} invalid files:")
        for entry in stats["invalid_files"][:10]:
            logger.warning(f"  [{entry['class']}] {entry['path']} — {entry['reason']}")
        if len(stats["invalid_files"]) > 10:
            logger.warning(f"  ... and {len(stats['invalid_files']) - 10} more")

    return stats


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Validate dataset images")
    parser.add_argument(
        "--data-dir",
        type=str,
        default="backend/data/processed",
        help="Path to processed dataset directory",
    )
    parser.add_argument(
        "--delete-invalid",
        action="store_true",
        help="Delete invalid files after validation",
    )
    args = parser.parse_args()

    results = validate_directory(args.data_dir)

    if args.delete_invalid and results["invalid_files"]:
        for entry in results["invalid_files"]:
            try:
                os.remove(entry["path"])
                logger.info(f"Deleted: {entry['path']}")
            except OSError as e:
                logger.error(f"Failed to delete {entry['path']}: {e}")
