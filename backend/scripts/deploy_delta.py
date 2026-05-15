"""Compute model delta, upload to Supabase, and register version.

Full pipeline for OTA model update:
  1. Compute .rkd delta between old and new TFLite model
  2. Upload delta (+ full model as fallback) to Supabase Storage
  3. Register version in model_versions table

Usage:
    uv run python backend/scripts/deploy_delta.py \\
        --old backend/models/retak_mobilenetv2_v3a.tflite \\
        --new backend/models/retak_mobilenetv2.tflite \\
        --version v3b \\
        --changelog "Improved WASPADA recall by 5%"

    # Only compute delta (no upload)
    uv run python backend/scripts/deploy_delta.py \\
        --old old.tflite --new new.tflite --version v3b --dry-run

Environment:
    SUPABASE_URL      — Supabase project URL
    SUPABASE_SERVICE_KEY — Service role key (admin)
"""

import argparse
import json
import logging
import os
import sys
from pathlib import Path

import requests

# Ensure project root is in path for sibling imports
_project_root = Path(__file__).resolve().parent.parent.parent
if str(_project_root) not in sys.path:
    sys.path.insert(0, str(_project_root))

from backend.scripts.training.compute_delta import build_delta, print_stats  # noqa: E402

logging.basicConfig(
    level=logging.INFO, format="%(asctime)s - %(levelname)s - %(message)s"
)
logger = logging.getLogger(__name__)

STORAGE_BUCKET = "model-deltas"
DELTA_DIR = "backend/models/delta"


def upload_to_supabase(
    local_path: str,
    storage_path: str,
    supabase_url: str,
    service_key: str,
) -> str:
    """Upload file to Supabase Storage. Returns public URL."""
    url = f"{supabase_url}/storage/v1/object/{STORAGE_BUCKET}/{storage_path}"
    headers = {
        "Authorization": f"Bearer {service_key}",
        "Content-Type": "application/octet-stream",
    }
    with open(local_path, "rb") as f:
        resp = requests.put(url, headers=headers, data=f)

    if resp.status_code not in (200, 201):
        raise RuntimeError(
            f"Upload failed ({resp.status_code}): {resp.text[:200]}"
        )

    # Get public URL
    public_url = f"{supabase_url}/storage/v1/object/public/{STORAGE_BUCKET}/{storage_path}"
    logger.info(f"  Uploaded: {public_url}")
    return public_url


def register_version(
    version: str,
    model_size: int,
    delta_size: int | None,
    delta_path: str | None,
    changelog: str,
    supabase_url: str,
    service_key: str,
):
    """Insert version record into model_versions table."""
    headers = {
        "Authorization": f"Bearer {service_key}",
        "Content-Type": "application/json",
    }

    # Deactivate previous active versions
    deactivate_url = f"{supabase_url}/rest/v1/model_versions"
    deactivate_payload = {"is_active": False}
    deactivate_query = {"is_active": "eq.true"}
    resp = requests.patch(
        deactivate_url,
        headers=headers,
        params=deactivate_query,
        json=deactivate_payload,
    )
    if resp.status_code not in (200, 204):
        logger.warning(f"  Deactivate previous failed: {resp.text[:200]}")

    # Insert new version
    insert_url = f"{supabase_url}/rest/v1/model_versions"
    insert_payload = {
        "version": version,
        "model_size_bytes": model_size,
        "delta_size_bytes": delta_size,
        "delta_path": delta_path,
        "changelog": changelog,
        "is_active": True,
    }
    resp = requests.post(insert_url, headers=headers, json=insert_payload)

    if resp.status_code not in (200, 201):
        raise RuntimeError(
            f"Register version failed ({resp.status_code}): {resp.text[:200]}"
        )
    logger.info(f"  Registered version '{version}' in model_versions")


def main():
    parser = argparse.ArgumentParser(
        description="Deploy model delta: compute → upload → register"
    )
    parser.add_argument("--old", required=True, help="Old TFLite model path")
    parser.add_argument("--new", required=True, help="New TFLite model path")
    parser.add_argument(
        "--version", required=True, help="Version identifier (e.g. v3b)"
    )
    parser.add_argument("--changelog", default="", help="Changelog for this version")
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Only compute delta and print report, no upload",
    )
    parser.add_argument(
        "--no-upload",
        action="store_true",
        help="Compute delta and save file, but skip Supabase upload",
    )
    args = parser.parse_args()

    old_path = Path(args.old)
    new_path = Path(args.new)
    version = args.version
    changelog = args.changelog

    if not old_path.exists():
        logger.error(f"Old model not found: {old_path}")
        sys.exit(1)
    if not new_path.exists():
        logger.error(f"New model not found: {new_path}")
        sys.exit(1)

    # 1. Compute delta
    delta_dir = Path(DELTA_DIR)
    delta_dir.mkdir(parents=True, exist_ok=True)
    delta_name = f"delta_{version}.rkd"
    delta_path = delta_dir / delta_name

    logger.info("─" * 50)
    logger.info("STEP 1: Computing delta…")
    logger.info("─" * 50)
    stats = build_delta(str(old_path), str(new_path), str(delta_path))
    print_stats(stats)

    if stats["changed_regions"] == 0:
        logger.warning("No changes detected. Nothing to deploy.")
        sys.exit(0)

    if stats["savings_vs_full"] < 50:
        logger.warning(
            f"Delta only saves {stats['savings_vs_full']}% — "
            "too large for delta. Deploying full model only."
        )
        # Reset delta path — edge function will return full_url
        delta_path = None

    if args.dry_run:
        logger.info("Dry-run — stopping here.")
        sys.exit(0)

    # 2. Upload to Supabase
    supabase_url = os.environ.get("SUPABASE_URL")
    supabase_key = os.environ.get("SUPABASE_SERVICE_KEY")

    if args.no_upload or not supabase_url or not supabase_key:
        logger.info("─" * 50)
        logger.info("STEP 2: Skipping upload (--no-upload or missing env vars)")
        if delta_path:
            logger.info(f"  Delta saved locally: {delta_path}")
        logger.info("  To upload later:")
        logger.info(
            f"    SUPABASE_URL=... SUPABASE_SERVICE_KEY=... {sys.argv[0]} --old {args.old} --new {args.new} --version {version}"
        )
        logger.info("─" * 50)
        sys.exit(0)

    logger.info("─" * 50)
    logger.info("STEP 2: Uploading to Supabase Storage…")
    logger.info("─" * 50)

    # Upload delta (if it was computed)
    storage_delta_path = None
    delta_size = None
    if delta_path and delta_path.exists():
        storage_delta_path = f"{version}/{delta_name}"
        upload_to_supabase(
            str(delta_path),
            storage_delta_path,
            supabase_url,
            supabase_key,
        )
        delta_size = stats["compressed_delta_size"]
    else:
        logger.info("  No delta file — full model only")

    # Upload full model as fallback
    full_storage_path = f"{version}/retak_mobilenetv2_{version}.tflite"
    upload_to_supabase(
        str(new_path),
        full_storage_path,
        supabase_url,
        supabase_key,
    )

    # 3. Register version in DB
    logger.info("─" * 50)
    logger.info("STEP 3: Registering version…")
    logger.info("─" * 50)
    register_version(
        version=version,
        model_size=stats["new_size"],
        delta_size=delta_size,
        delta_path=storage_delta_path,
        changelog=changelog,
        supabase_url=supabase_url,
        service_key=supabase_key,
    )

    logger.info("─" * 50)
    logger.info("✅ DEPLOY COMPLETE")
    logger.info("─" * 50)
    logger.info(f"  Version:       {version}")
    if delta_size:
        logger.info(f"  Delta:         {delta_size:,} bytes")
    logger.info(f"  Full model:    {stats['new_size']:,} bytes")
    if delta_size:
        logger.info(f"  Savings:       {stats['savings_vs_full']}%")
    logger.info("─" * 50)
    logger.info("Android app will auto-detect update on next launch.")


if __name__ == "__main__":
    main()
