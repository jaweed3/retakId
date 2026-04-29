"""Compute dataset statistics and class distribution.

Usage:
    uv run python backend/scripts/processing/dataset_stats.py
    uv run python backend/scripts/processing/dataset_stats.py --data-dir backend/data/processed
"""

import argparse
import logging
import json
from pathlib import Path
from collections import defaultdict

import numpy as np
from PIL import Image

LOG_FORMAT = "%(asctime)s - %(levelname)s - %(message)s"
logging.basicConfig(level=logging.INFO, format=LOG_FORMAT)
logger = logging.getLogger(__name__)


def compute_stats(data_dir: str) -> dict:
    """Compute per-class and overall dataset statistics.

    Returns dict suitable for JSON serialization.
    """
    data_path = Path(data_dir)
    if not data_path.exists():
        logger.error(f"Directory not found: {data_dir}")
        raise SystemExit(1)

    class_dirs = sorted(
        [d for d in data_path.iterdir() if d.is_dir() and not d.name.startswith(".")]
    )
    if not class_dirs:
        logger.error(f"No class directories found in {data_dir}")
        raise SystemExit(1)

    image_exts = {".jpg", ".jpeg", ".png", ".bmp", ".gif", ".webp"}

    per_class = {}
    all_widths = []
    all_heights = []
    all_aspect_ratios = []
    all_file_sizes_kb = []
    mean_colors_by_class = {}

    for class_dir in class_dirs:
        class_name = class_dir.name
        images = sorted(
            [f for f in class_dir.glob("*") if f.suffix.lower() in image_exts]
        )
        n = len(images)

        if n == 0:
            logger.warning(f"  {class_name}: 0 images")
            per_class[class_name] = {"count": 0}
            continue

        widths, heights, aspects, sizes_kb = [], [], [], []
        class_pixels_sum = np.zeros(3, dtype=np.float64)
        class_pixel_count = 0

        for img_path in images:
            try:
                with Image.open(img_path) as img:
                    w, h = img.size
                    widths.append(w)
                    heights.append(h)
                    aspects.append(w / h if h > 0 else 0)
                    sizes_kb.append(img_path.stat().st_size / 1024)

                    # Sample pixels for mean color (resize small then average)
                    img_small = img.resize((32, 32))
                    arr = np.array(img_small, dtype=np.float64)
                    if arr.ndim == 3 and arr.shape[2] >= 3:
                        class_pixels_sum += arr[:, :, :3].reshape(-1, 3).sum(axis=0)
                        class_pixel_count += arr[:, :, :3].reshape(-1, 3).shape[0]
            except Exception:
                continue

        mean_color = (
            (class_pixels_sum / class_pixel_count).tolist()
            if class_pixel_count > 0
            else [0, 0, 0]
        )

        per_class[class_name] = {
            "count": n,
            "width": {
                "min": int(np.min(widths)),
                "max": int(np.max(widths)),
                "mean": float(np.mean(widths)),
                "median": float(np.median(widths)),
            },
            "height": {
                "min": int(np.min(heights)),
                "max": int(np.max(heights)),
                "mean": float(np.mean(heights)),
                "median": float(np.median(heights)),
            },
            "aspect_ratio": {
                "min": float(np.min(aspects)),
                "max": float(np.max(aspects)),
                "mean": float(np.mean(aspects)),
            },
            "file_size_kb": {
                "min": float(np.min(sizes_kb)),
                "max": float(np.max(sizes_kb)),
                "mean": float(np.mean(sizes_kb)),
            },
            "mean_rgb": [round(c, 1) for c in mean_color],
        }

        all_widths.extend(widths)
        all_heights.extend(heights)
        all_aspect_ratios.extend(aspects)
        all_file_sizes_kb.extend(sizes_kb)
        mean_colors_by_class[class_name] = mean_color

    # Overall
    total = sum(c["count"] for c in per_class.values())
    summary = {
        "total_images": total,
        "classes": len(per_class),
        "class_distribution": {k: v["count"] for k, v in per_class.items()},
        "class_balance_ratio": (
            min(v["count"] for v in per_class.values() if v["count"] > 0)
            / max(v["count"] for v in per_class.values())
            if total > 0
            else 0
        ),
        "overall_width_mean": float(np.mean(all_widths)) if all_widths else 0,
        "overall_height_mean": float(np.mean(all_heights)) if all_heights else 0,
        "overall_aspect_mean": (
            float(np.mean(all_aspect_ratios)) if all_aspect_ratios else 0
        ),
        "overall_file_size_kb_mean": (
            float(np.mean(all_file_sizes_kb)) if all_file_sizes_kb else 0
        ),
        "per_class": per_class,
    }

    return summary


def print_summary(stats: dict) -> None:
    """Pretty-print the dataset statistics."""
    total = stats["total_images"]
    logger.info(f"Dataset: {total} images across {stats['classes']} classes")
    logger.info(
        f"Class balance ratio (min/max): {stats['class_balance_ratio']:.2f}"
    )
    logger.info("")

    for cls, info in stats["per_class"].items():
        if info["count"] == 0:
            continue
        logger.info(
            f"  {cls}: {info['count']} images "
            f"({info['count']/total*100:.1f}%)"
            if total > 0
            else f"  {cls}: {info['count']} images"
        )
        logger.info(
            f"    Size: {info['width']['mean']:.0f}x{info['height']['mean']:.0f} "
            f"(range: {info['width']['min']}-{info['width']['max']} x "
            f"{info['height']['min']}-{info['height']['max']})"
        )
        logger.info(
            f"    Aspect ratio: {info['aspect_ratio']['mean']:.2f} "
            f"(range: {info['aspect_ratio']['min']:.2f}-{info['aspect_ratio']['max']:.2f})"
        )
        logger.info(f"    File size: {info['file_size_kb']['mean']:.0f} KB avg")
        logger.info(
            f"    Mean RGB: ({info['mean_rgb'][0]:.0f}, "
            f"{info['mean_rgb'][1]:.0f}, {info['mean_rgb'][2]:.0f})"
        )
        logger.info("")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Dataset statistics")
    parser.add_argument(
        "--data-dir",
        type=str,
        default="backend/data/processed",
        help="Path to processed dataset directory",
    )
    parser.add_argument(
        "--json",
        type=str,
        default=None,
        help="Save stats as JSON file",
    )
    args = parser.parse_args()

    stats = compute_stats(args.data_dir)
    print_summary(stats)

    if args.json:
        with open(args.json, "w") as f:
            json.dump(stats, f, indent=2)
        logger.info(f"Saved stats to {args.json}")
