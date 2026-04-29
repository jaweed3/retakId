import os
import time
import uuid
import json
import logging
import hashlib
from concurrent.futures import ThreadPoolExecutor, as_completed
from io import BytesIO

import requests
import imagehash
import numpy as np
from duckduckgo_search import DDGS
from PIL import Image, UnidentifiedImageError

LOG_FORMAT = "%(asctime)s - %(levelname)s - %(message)s"
logging.basicConfig(level=logging.INFO, format=LOG_FORMAT)
logger = logging.getLogger(__name__)

# --- Quality thresholds ---
MIN_WIDTH = 200
MIN_HEIGHT = 200
BLUR_THRESHOLD = 100  # Laplacian variance below this = blurry
PHASH_HAMMING_THRESHOLD = 6  # hamming distance below this = near-duplicate
MAX_RETRIES = 3
RETRY_BACKOFF = 2  # seconds multiplier (1s, 2s, 4s)


def _compute_phash(img: Image.Image) -> imagehash.ImageHash:
    """Compute perceptual hash of an image."""
    return imagehash.phash(img)


def _blur_score(img: Image.Image) -> float:
    """Laplacian variance blur score. Higher = sharper."""
    gray = img.convert("L")
    arr = np.array(gray, dtype=np.float64)
    laplacian = np.array(
        [[0, 1, 0], [1, -4, 1], [0, 1, 0]], dtype=np.float64
    )
    import cv2

    lap = cv2.Laplacian(np.array(gray), cv2.CV_64F)
    return float(lap.var())


def _validate_quality(img: Image.Image) -> tuple[bool, str]:
    """Check if image meets quality thresholds."""
    w, h = img.size
    if w < MIN_WIDTH or h < MIN_HEIGHT:
        return False, f"too_small ({w}x{h})"
    return True, "ok"


def _validate_blur(img: Image.Image) -> tuple[bool, str]:
    """Check if image is too blurry."""
    score = _blur_score(img)
    if score < BLUR_THRESHOLD:
        return False, f"blurry (score={score:.1f})"
    return True, "ok"


class RetakScraper:
    def __init__(self, base_output_dir="backend/data/raw"):
        self.base_output_dir = base_output_dir
        os.makedirs(self.base_output_dir, exist_ok=True)
        self._seen_hashes: set[str] = set()

    def download_image(self, url: str, folder_path: str) -> dict:
        """Download a single image with retry, validate, deduplicate, and save."""
        for attempt in range(1, MAX_RETRIES + 1):
            try:
                response = requests.get(url, timeout=10)
                response.raise_for_status()

                img = Image.open(BytesIO(response.content))
                img = img.convert("RGB")

                # --- Quality checks ---
                ok, reason = _validate_quality(img)
                if not ok:
                    return {"url": url, "status": "rejected", "reason": reason}
                ok, reason = _validate_blur(img)
                if not ok:
                    return {"url": url, "status": "rejected", "reason": reason}

                # --- Deduplication via perceptual hash ---
                phash = _compute_phash(img)
                if str(phash) in self._seen_hashes:
                    return {
                        "url": url,
                        "status": "rejected",
                        "reason": "duplicate_phash",
                    }
                # Also check hamming distance against all seen hashes
                for seen in list(self._seen_hashes)[-500:]:  # only recent 500
                    if phash - imagehash.hex_to_hash(seen) < PHASH_HAMMING_THRESHOLD:
                        return {
                            "url": url,
                            "status": "rejected",
                            "reason": "near_duplicate",
                        }
                self._seen_hashes.add(str(phash))

                # --- Save ---
                filename = f"{uuid.uuid4().hex}.jpg"
                file_path = os.path.join(folder_path, filename)
                img.save(file_path, "JPEG", quality=85)

                return {
                    "url": url,
                    "path": file_path,
                    "status": "success",
                    "phash": str(phash),
                }

            except (UnidentifiedImageError, OSError):
                return {"url": url, "status": "rejected", "reason": "corrupt_image"}
            except (requests.ConnectionError, requests.Timeout) as e:
                if attempt < MAX_RETRIES:
                    sleep_sec = RETRY_BACKOFF ** (attempt - 1)
                    logger.debug(f"Retry {attempt}/{MAX_RETRIES} in {sleep_sec}s: {url}")
                    time.sleep(sleep_sec)
                else:
                    return {
                        "url": url,
                        "status": "failed",
                        "reason": f"network_retry_exhausted: {e}",
                    }
            except Exception as e:
                return {"url": url, "status": "failed", "reason": str(e)}
        return {"url": url, "status": "failed", "reason": "unknown"}

    def scrape_keyword(self, keyword: str, max_images: int = 100) -> list[dict]:
        """Scrape images for a keyword with full quality pipeline."""
        folder_path = os.path.join(
            self.base_output_dir, keyword.replace(" ", "_")
        )
        os.makedirs(folder_path, exist_ok=True)

        logger.info(f"Searching for '{keyword}' (target: {max_images})...")

        with DDGS() as ddgs:
            results = ddgs.images(
                keyword,
                region="wt-wt",
                safesearch="off",
                size="Medium",
                max_results=max_images,
            )
            urls = [r["image"] for r in results]

        logger.info(f"Found {len(urls)} candidates. Downloading...")

        manifest = []
        success_count = 0
        rejected_count = 0
        failed_count = 0

        with ThreadPoolExecutor(max_workers=8) as executor:
            futures = {
                executor.submit(self.download_image, url, folder_path): url
                for url in urls
            }
            for future in as_completed(futures):
                res = future.result()
                manifest.append(res)
                if res["status"] == "success":
                    success_count += 1
                elif res["status"] == "rejected":
                    rejected_count += 1
                else:
                    failed_count += 1

        logger.info(
            f"'{keyword}': success={success_count}, "
            f"rejected={rejected_count}, failed={failed_count}"
        )

        # Save manifest
        manifest_path = os.path.join(folder_path, "manifest.json")
        with open(manifest_path, "w") as f:
            json.dump(manifest, f, indent=2)

        return manifest


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description="Retak.id Image Scraper")
    parser.add_argument(
        "--keywords",
        type=str,
        required=True,
        help="Comma-separated keywords",
    )
    parser.add_argument(
        "--limit",
        type=int,
        default=100,
        help="Max images per keyword",
    )
    args = parser.parse_args()

    scraper = RetakScraper()
    keywords = [k.strip() for k in args.keywords.split(",")]

    for kw in keywords:
        scraper.scrape_keyword(kw, max_images=args.limit)
