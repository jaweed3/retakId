"""Compute delta between two TFLite models (old → new) for OTA weight-only updates.

Output: .rkd (Retak Delta) file — gzip-compressed changed byte regions.

Usage:
    uv run python backend/scripts/training/compute_delta.py \\
        --old backend/models/retak_mobilenetv2_v3a.tflite \\
        --new backend/models/retak_mobilenetv2.tflite \\
        --out backend/models/delta/rkd_v3a_to_v3b.rkd

    # Dry-run: stats only, no file written
    uv run python backend/scripts/training/compute_delta.py \\
        --old old.tflite --new new.tflite --dry-run
"""

import argparse
import gzip
import logging
import struct
import sys
from pathlib import Path

logging.basicConfig(
    level=logging.INFO, format="%(asctime)s - %(levelname)s - %(message)s"
)
logger = logging.getLogger(__name__)

RKD_MAGIC = b"RKD1"
DELTA_DIR = "backend/models/delta"


def find_changed_regions(old: bytes, new: bytes) -> list[tuple[int, int, bytes]]:
    """Find contiguous byte regions that differ between old and new."""
    if len(old) != len(new):
        logger.warning(
            f"Size mismatch: old={len(old)} bytes, new={len(new)} bytes. "
            "Likely architecture change — using full model fallback."
        )
        # If sizes differ, whole new model is the "delta"
        return [(0, len(new), new)]

    regions: list[tuple[int, int, bytes]] = []
    i = 0
    n = len(old)

    while i < n:
        if old[i] != new[i]:
            start = i
            while i < n and old[i] != new[i]:
                i += 1
            end = i
            regions.append((start, end - start, new[start:end]))
        else:
            i += 1

    return regions


def build_delta(old_path: str, new_path: str, out_path: str | None = None) -> dict:
    """Compute delta and write .rkd file. Returns stats dict."""
    old_bytes = Path(old_path).read_bytes()
    new_bytes = Path(new_path).read_bytes()
    old_size = len(old_bytes)
    new_size = len(new_bytes)

    logger.info(f"Old model: {old_path}  ({old_size:,} bytes)")
    logger.info(f"New model: {new_path}  ({new_size:,} bytes)")

    regions = find_changed_regions(old_bytes, new_bytes)
    total_changed = sum(length for _, length, _ in regions)
    pct_changed = (total_changed / max(old_size, 1)) * 100

    logger.info(f"Changed regions: {len(regions)}")
    logger.info(f"Total changed bytes: {total_changed:,} ({pct_changed:.1f}%)")

    # Build RKD payload
    payload = bytearray()
    payload.extend(RKD_MAGIC)
    payload.extend(struct.pack("<I", len(regions)))  # number of regions

    for offset, length, data in regions:
        payload.extend(struct.pack("<II", offset, length))
        payload.extend(data)

    raw_size = len(payload)
    compressed = gzip.compress(bytes(payload), compresslevel=9)
    compressed_size = len(compressed)

    stats = {
        "old_size": old_size,
        "new_size": new_size,
        "changed_regions": len(regions),
        "total_changed": total_changed,
        "pct_changed": round(pct_changed, 1),
        "raw_delta_size": raw_size,
        "compressed_delta_size": compressed_size,
        "compression_ratio": (
            round((1 - compressed_size / raw_size) * 100, 1) if raw_size else 0
        ),
        "savings_vs_full": (
            round((1 - compressed_size / new_size) * 100, 1) if new_size else 0
        ),
    }

    if out_path:
        if stats["changed_regions"] == 0:
            logger.info("No changes — skipping delta file write")
        else:
            Path(out_path).parent.mkdir(parents=True, exist_ok=True)
            Path(out_path).write_bytes(compressed)
            stats["delta_path"] = out_path
            logger.info(f"Delta written: {out_path}  ({compressed_size:,} bytes)")

    return stats


def print_stats(stats: dict):
    sep = "─" * 50
    logger.info(f"\n{sep}")
    logger.info("  DELTA REPORT")
    logger.info(sep)
    logger.info(f"  Old model size:           {stats['old_size']:>10,} bytes")
    logger.info(f"  New model size:           {stats['new_size']:>10,} bytes")
    logger.info(f"  Changed regions:          {stats['changed_regions']:>10}")
    logger.info(f"  Total changed bytes:      {stats['total_changed']:>10,}  ({stats['pct_changed']}%)")
    logger.info(f"  Raw delta size:           {stats['raw_delta_size']:>10,} bytes")
    logger.info(f"  Compressed delta:         {stats['compressed_delta_size']:>10,} bytes")
    logger.info(f"  Compression savings:      {stats['compression_ratio']}%")
    logger.info(sep)
    logger.info(f"  Savings vs full download: {stats['savings_vs_full']}%")
    logger.info(f"  From {stats['new_size']:,} B → {stats['compressed_delta_size']:,} B")
    if "delta_path" in stats:
        logger.info(f"  Delta file: {stats['delta_path']}")
    logger.info(sep)
    # Recommendation
    if stats["compressed_delta_size"] < stats["new_size"] * 0.5:
        logger.info("  ✅ RECOMMENDATION: Use delta update")
    elif stats["compressed_delta_size"] < stats["new_size"] * 0.8:
        logger.info("  ⚠️  RECOMMENDATION: Delta is moderate — still better than full")
    else:
        logger.info("  ❌ RECOMMENDATION: Delta too large — use full model instead")
    logger.info(f"{sep}\n")


def main():
    parser = argparse.ArgumentParser(
        description="Compute TFLite model delta for OTA weight-only updates"
    )
    parser.add_argument("--old", required=True, help="Old TFLite model path")
    parser.add_argument("--new", required=True, help="New TFLite model path")
    parser.add_argument(
        "--out",
        default=None,
        help="Output .rkd delta file path (default: backend/models/delta/delta_<new_filename>.rkd)",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Print stats only, don't write delta file",
    )
    args = parser.parse_args()

    if not Path(args.old).exists():
        logger.error(f"Old model not found: {args.old}")
        sys.exit(1)
    if not Path(args.new).exists():
        logger.error(f"New model not found: {args.new}")
        sys.exit(1)

    out_path = args.out
    if args.dry_run:
        out_path = None
    elif out_path is None:
        # Auto-generate: backend/models/delta/delta_<new_basename>.rkd
        new_name = Path(args.new).stem
        out_path = str(Path(DELTA_DIR) / f"delta_{new_name}.rkd")

    stats = build_delta(args.old, args.new, out_path)
    print_stats(stats)


if __name__ == "__main__":
    main()
