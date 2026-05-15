"""Ingest admin-verified training data from CSV → retraining dataset.

Flow:
  1. Read CSV exported from web admin (Export Training Data)
  2. Download each foto from Supabase public URL
  3. Perceptual hash dedup against existing dataset
  4. Save to backend/data/processed/{label_akhir}/training_{id}.jpg
  5. Print ingestion report

Usage:
    uv run python backend/scripts/training/ingest_verification.py --csv training_export.csv
    uv run python backend/scripts/training/ingest_verification.py --csv training_export.csv --no-dedup
"""

import argparse
import logging
import sys
import uuid
from pathlib import Path

import pandas as pd
import requests
from PIL import Image, UnidentifiedImageError

try:
    import imagehash
except ImportError:
    imagehash = None

LOG_FORMAT = "%(asctime)s - %(levelname)s - %(message)s"
logging.basicConfig(level=logging.INFO, format=LOG_FORMAT)
logger = logging.getLogger(__name__)

VALID_CLASSES = {"AMAN", "WASPADA", "BAHAYA"}
IMAGE_EXTS = {".jpg", ".jpeg", ".png", ".bmp", ".gif", ".webp"}
DVC_PROCESSED = Path("backend/data/processed")
HASH_THRESHOLD = 6
MAX_FILE_SIZE_MB = 20
MIN_DIM = 100
TIMEOUT = 30


def download_image(url: str, dest: Path) -> bool:
    """Download image from URL to dest. Returns True on success."""
    try:
        resp = requests.get(url, timeout=TIMEOUT)
        resp.raise_for_status()
        dest.write_bytes(resp.content)
        return True
    except Exception as e:
        logger.warning(f"Download failed: {url[:80]}… → {e}")
        return False


def validate_image(path: Path) -> bool:
    """Validate image file integrity and minimum dimensions."""
    try:
        size_mb = path.stat().st_size / (1024 * 1024)
        if size_mb > MAX_FILE_SIZE_MB:
            logger.warning(f"  Too large ({size_mb:.1f} MB): {path.name}")
            return False
        with Image.open(path) as img:
            img.verify()
        with Image.open(path) as img:
            w, h = img.size
            if w < MIN_DIM or h < MIN_DIM:
                logger.warning(f"  Too small ({w}x{h}): {path.name}")
                return False
        return True
    except (UnidentifiedImageError, OSError, IOError) as e:
        logger.warning(f"  Corrupt image: {path.name} → {e}")
        return False


def build_hash_index(data_dir: Path) -> dict:
    """Build a dict of phash → Path for all existing images."""
    index = {}
    if not data_dir.exists():
        return index
    if imagehash is None:
        return index
    for class_dir in data_dir.iterdir():
        if not class_dir.is_dir() or class_dir.name.startswith("."):
            continue
        for img_path in class_dir.iterdir():
            if img_path.suffix.lower() not in IMAGE_EXTS:
                continue
            try:
                phash = imagehash.phash(Image.open(img_path))
                index[phash] = img_path
            except Exception:
                continue
    return index


def find_duplicate(
    img_path: Path, hash_index: dict, threshold: int = HASH_THRESHOLD
) -> tuple:
    """Check if img_path is a near-duplicate of any existing image.
    Returns (existing_path, hamming_distance) or None.
    """
    if imagehash is None or not hash_index:
        return None
    try:
        phash = imagehash.phash(Image.open(img_path))
    except Exception:
        return None
    for existing_hash, existing_path in hash_index.items():
        dist = phash - existing_hash
        if dist <= threshold:
            return (existing_path, dist)
    return None


def ingest(csv_path: str, data_dir: str, dedup: bool = True) -> dict:
    """Main ingestion pipeline. Returns report dict."""
    data_path = Path(data_dir)
    data_path.mkdir(parents=True, exist_ok=True)
    for cls in VALID_CLASSES:
        (data_path / cls).mkdir(exist_ok=True)

    df = pd.read_csv(csv_path)
    logger.info(f"Loaded {len(df)} records from {csv_path}")

    # Filter valid rows
    df = df[df["label_akhir"].isin(VALID_CLASSES)].copy()
    logger.info(f"  {len(df)} have valid label_akhir ({', '.join(sorted(VALID_CLASSES))})")

    # Build dedup index
    hash_index = {}
    if dedup and imagehash is not None:
        logger.info("Building perceptual hash index of existing dataset…")
        hash_index = build_hash_index(data_path)
        logger.info(f"  Indexed {len(hash_index)} existing images")
    elif dedup and imagehash is None:
        logger.warning("imagehash not installed — skipping dedup")
        dedup = False

    report = {
        "total": len(df),
        "downloaded": 0,
        "valid": 0,
        "skipped_existing": 0,
        "skipped_dup": 0,
        "skipped_invalid": 0,
        "failed_download": 0,
        "per_class": {c: {"added": 0} for c in VALID_CLASSES},
        "errors": [],
    }

    for _, row in df.iterrows():
        foto_url = str(row["foto_url"]).strip()
        label = str(row["label_akhir"]).strip()
        laporan_id = row.get("laporan_id", "")

        if not foto_url or not foto_url.startswith("http"):
            report["errors"].append(f"Row {_}: invalid foto_url → {foto_url[:60]}")
            continue

        # Unique filename: training_{laporan_id}_{uuid}.jpg
        unique_id = uuid.uuid4().hex[:8]
        fname = f"training_{laporan_id}_{unique_id}.jpg"
        dest = data_path / label / fname

        if dest.exists():
            report["skipped_existing"] += 1
            continue

        # Download
        if not download_image(foto_url, dest):
            report["failed_download"] += 1
            dest.unlink(missing_ok=True)
            continue
        report["downloaded"] += 1

        # Validate
        if not validate_image(dest):
            report["skipped_invalid"] += 1
            dest.unlink(missing_ok=True)
            continue
        report["valid"] += 1

        # Dedup
        if dedup:
            dup = find_duplicate(dest, hash_index)
            if dup:
                existing_path, dist = dup
                logger.info(f"  Duplicate (hamming={dist}) with {existing_path.name} — skipping")
                report["skipped_dup"] += 1
                dest.unlink(missing_ok=True)
                continue
            # Index the new image so intra-batch dedup works
            try:
                phash = imagehash.phash(Image.open(dest))
                hash_index[phash] = dest
            except Exception:
                pass

        report["per_class"][label]["added"] += 1

    return report


def print_report(report: dict):
    """Pretty-print ingestion report."""
    sep = "─" * 50
    logger.info(f"\n{sep}")
    logger.info("  INGESTION REPORT")
    logger.info(sep)
    logger.info(f"  Total records in CSV:    {report['total']}")
    logger.info(f"  Downloaded:              {report['downloaded']}")
    logger.info(f"  Valid (saved):           {report['valid']}")
    logger.info(f"  Failed download:         {report['failed_download']}")
    logger.info(f"  Skipped (invalid):       {report['skipped_invalid']}")
    logger.info(f"  Skipped (duplicate):     {report['skipped_dup']}")
    logger.info(f"  Skipped (already exists): {report['skipped_existing']}")
    logger.info(sep)
    logger.info("  Per-class additions:")
    for cls in sorted(VALID_CLASSES):
        logger.info(f"    {cls}: +{report['per_class'][cls]['added']}")
    logger.info(sep)
    if report["errors"]:
        logger.warning(f"  Errors ({len(report['errors'])}):")
        for err in report["errors"][:10]:
            logger.warning(f"    • {err}")
        if len(report["errors"]) > 10:
            logger.warning(f"    … and {len(report['errors']) - 10} more")
        logger.info(sep)
    logger.info(f"  Next: make split && make train\n")


def main():
    parser = argparse.ArgumentParser(
        description="Ingest admin-verified training data from CSV into processed dataset"
    )
    parser.add_argument(
        "--csv",
        required=True,
        help="Path to CSV exported from web admin (Export Training Data)",
    )
    parser.add_argument(
        "--data-dir",
        default=str(DVC_PROCESSED),
        help=f"Dataset root (default: {DVC_PROCESSED})",
    )
    parser.add_argument(
        "--no-dedup",
        action="store_true",
        help="Skip perceptual hash deduplication",
    )
    args = parser.parse_args()

    csv_path = Path(args.csv)
    if not csv_path.exists():
        logger.error(f"CSV not found: {args.csv}")
        sys.exit(1)

    report = ingest(str(csv_path), args.data_dir, dedup=not args.no_dedup)
    print_report(report)

    if report["valid"] == 0:
        logger.warning("No new images ingested — nothing to train on")
        sys.exit(0)

    logger.info("Run: make split && make train")


if __name__ == "__main__":
    main()
