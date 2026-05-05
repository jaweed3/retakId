"""Cross-class perceptual hash deduplication.

Detects images that are near-duplicates across different classes,
which would cause data leakage and inflated evaluation metrics.

Usage:
    uv run python backend/scripts/processing/deduplicate.py
    uv run python backend/scripts/processing/deduplicate.py --data-dir backend/data/processed --threshold 6
"""

import argparse
import logging
from pathlib import Path
from collections import defaultdict

import imagehash
from PIL import Image

LOG_FORMAT = "%(asctime)s - %(levelname)s - %(message)s"
logging.basicConfig(level=logging.INFO, format=LOG_FORMAT)
logger = logging.getLogger(__name__)

IMAGE_EXTS = {".jpg", ".jpeg", ".png", ".bmp", ".gif", ".webp"}


def find_cross_class_duplicates(
    data_dir: str,
    hamming_threshold: int = 6,
    delete_duplicates: bool = False,
) -> list[dict]:
    """Find near-duplicate images across different classes.

    Returns list of duplicate pairs: [(path1, class1, path2, class2, hamming_dist), ...]
    """
    data_path = Path(data_dir)
    if not data_path.exists():
        logger.error(f"Directory not found: {data_dir}")
        raise SystemExit(1)

    class_dirs = sorted(
        [d for d in data_path.iterdir() if d.is_dir() and not d.name.startswith(".")]
    )
    if len(class_dirs) < 2:
        logger.warning("Need at least 2 classes to check cross-class duplicates")
        return []

    # Build hash index: {phash_str: [(path, class_name), ...]}
    logger.info("Computing perceptual hashes...")
    hash_index = defaultdict(list)
    total_images = 0

    for class_dir in class_dirs:
        class_name = class_dir.name
        images = sorted(
            [
                f
                for f in class_dir.glob("*")
                if f.suffix.lower() in IMAGE_EXTS
            ]
        )
        for img_path in images:
            try:
                with Image.open(img_path) as img:
                    ph = imagehash.phash(img)
                hash_index[str(ph)].append((str(img_path), class_name))
                total_images += 1
            except Exception as e:
                logger.debug(f"Failed to hash {img_path}: {e}")

    logger.info(f"Hashed {total_images} images, {len(hash_index)} unique hashes")

    # Find near-duplicates across classes
    hashes = list(hash_index.keys())
    duplicates = []
    checked = 0

    for i in range(len(hashes)):
        for j in range(i + 1, len(hashes)):
            h1 = imagehash.hex_to_hash(hashes[i])
            h2 = imagehash.hex_to_hash(hashes[j])
            dist = h1 - h2

            if dist < hamming_threshold:
                entries_i = hash_index[hashes[i]]
                entries_j = hash_index[hashes[j]]

                # Check if the hash buckets contain DIFFERENT classes
                classes_i = {e[1] for e in entries_i}
                classes_j = {e[1] for e in entries_j}

                if classes_i != classes_j or len(classes_i | classes_j) > 1:
                    for ei in entries_i:
                        for ej in entries_j:
                            if ei[1] != ej[1]:  # different class
                                duplicates.append(
                                    {
                                        "path_a": ei[0],
                                        "class_a": ei[1],
                                        "path_b": ej[0],
                                        "class_b": ej[1],
                                        "hamming_distance": dist,
                                    }
                                )

    # Deduplicate the duplicate list (same pair can appear multiple times)
    seen_pairs = set()
    unique_dupes = []
    for d in duplicates:
        key = tuple(sorted([(d["path_a"], d["class_a"]), (d["path_b"], d["class_b"])]))
        if key not in seen_pairs:
            seen_pairs.add(key)
            unique_dupes.append(d)

    return unique_dupes


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="Cross-class duplicate detection via perceptual hash"
    )
    parser.add_argument(
        "--data-dir",
        type=str,
        default="backend/data/processed",
        help="Path to processed dataset directory",
    )
    parser.add_argument(
        "--threshold",
        type=int,
        default=6,
        help="Hamming distance threshold (lower = stricter, default: 6)",
    )
    parser.add_argument(
        "--delete",
        action="store_true",
        help="Delete duplicate images from the larger class",
    )
    args = parser.parse_args()

    dupes = find_cross_class_duplicates(
        data_dir=args.data_dir,
        hamming_threshold=args.threshold,
        delete_duplicates=args.delete,
    )

    if not dupes:
        logger.info("No cross-class duplicates found.")
    else:
        logger.warning(
            f"Found {len(dupes)} cross-class near-duplicate pairs "
            f"(threshold ≤ {args.threshold}):"
        )
        for d in dupes[:20]:
            logger.warning(
                f"  [{d['class_a']}] {d['path_a']}"
                f"  <-> [{d['class_b']}] {d['path_b']}"
                f"  (hamming={d['hamming_distance']})"
            )
        if len(dupes) > 20:
            logger.warning(f"  ... and {len(dupes) - 20} more")

        if args.delete:
            import os

            # Delete from larger class to preserve balance
            class_counts = {}
            for d in dupes:
                for cls in [d["class_a"], d["class_b"]]:
                    if cls not in class_counts:
                        class_counts[cls] = len(
                            [
                                f
                                for f in Path(args.data_dir, cls).glob("*")
                                if f.suffix.lower() in IMAGE_EXTS
                            ]
                        )

            deleted = 0
            for d in dupes:
                to_delete = (
                    d["path_a"]
                    if class_counts.get(d["class_a"], 0)
                    >= class_counts.get(d["class_b"], 0)
                    else d["path_b"]
                )
                try:
                    os.remove(to_delete)
                    deleted += 1
                except OSError as e:
                    logger.error(f"Failed to delete {to_delete}: {e}")
            logger.info(f"Deleted {deleted} duplicate images")
