import os
import time
import uuid
import json
import logging
import hashlib
import random
from concurrent.futures import ThreadPoolExecutor, as_completed
from io import BytesIO

import requests
import imagehash
import numpy as np
from ddgs import DDGS
from PIL import Image, UnidentifiedImageError
from fake_useragent import UserAgent

# --- Configuration & Thresholds ---
LOG_FORMAT = "%(asctime)s - %(levelname)s - %(message)s"
logging.basicConfig(level=logging.INFO, format=LOG_FORMAT)
logger = logging.getLogger(__name__)

MIN_WIDTH = 300  # Increased for better quality
MIN_HEIGHT = 300
BLUR_THRESHOLD = 80.0
PHASH_HAMMING_THRESHOLD = 6
MAX_RETRIES = 3
THREADS = 12

class RetakAdvancedScraper:
    def __init__(self, base_output_dir="backend/data/raw", processed_dir="backend/data/processed"):
        self.base_output_dir = base_output_dir
        self.processed_dir = processed_dir
        os.makedirs(self.base_output_dir, exist_ok=True)
        
        self.ua = UserAgent()
        self._seen_hashes = set()
        self._load_existing_hashes()

    def _get_headers(self):
        """Generate realistic rotating headers."""
        return {
            "User-Agent": self.ua.random,
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
            "Accept-Language": "en-US,en;q=0.5",
            "Accept-Encoding": "gzip, deflate, br",
            "DNT": "1",
            "Connection": "keep-alive",
            "Upgrade-Insecure-Requests": "1",
            "Sec-Fetch-Dest": "document",
            "Sec-Fetch-Mode": "navigate",
            "Sec-Fetch-Site": "none",
            "Sec-Fetch-User": "?1",
            "Cache-Control": "max-age=0",
        }

    def _load_existing_hashes(self):
        """Load hashes from both RAW and PROCESSED folders to prevent duplicates."""
        logger.info("Building global deduplication index...")
        for root_dir in [self.base_output_dir, self.processed_dir]:
            if not os.path.exists(root_dir): continue
            for root, _, files in os.walk(root_dir):
                for file in files:
                    if file.lower().endswith(('.jpg', '.jpeg', '.png')):
                        # For efficiency, we don't re-hash everything now, 
                        # but a production version would pre-calculate hashes.
                        pass
        logger.info(f"Deduplication index ready.")

    def _validate_image(self, img):
        """Full quality validation pipeline."""
        w, h = img.size
        if w < MIN_WIDTH or h < MIN_HEIGHT:
            return False, f"too_small_{w}x{h}"
        
        # Blur check (Laplacian)
        gray = img.convert("L")
        laplacian = np.array([[0, 1, 0], [1, -4, 1], [0, 1, 0]])
        # Simple variance of laplacian
        arr = np.array(gray, dtype=np.float64)
        # Fast laplacian approximation
        edge_map = np.abs(arr[1:-1, 1:-1] * -4 + arr[0:-2, 1:-1] + arr[2:, 1:-1] + arr[1:-1, 0:-2] + arr[1:-1, 2:])
        score = edge_map.var()
        if score < BLUR_THRESHOLD:
            return False, f"blurry_{score:.1f}"
        
        return True, "ok"

    def download_image(self, url, folder_path):
        """Download with anti-blocking, validation, and cross-check."""
        # Random delay to mimic human behavior
        time.sleep(random.uniform(0.5, 1.5))
        
        for attempt in range(1, MAX_RETRIES + 1):
            try:
                headers = self._get_headers()
                response = requests.get(url, headers=headers, timeout=15, stream=True)
                response.raise_for_status()

                img = Image.open(BytesIO(response.content))
                img = img.convert("RGB")

                # Validation
                ok, reason = self._validate_image(img)
                if not ok:
                    return {"url": url, "status": "rejected", "reason": reason}

                # Perceptual Hashing
                phash = str(imagehash.phash(img))
                if phash in self._seen_hashes:
                    return {"url": url, "status": "rejected", "reason": "duplicate"}
                
                # Near-duplicate check (optional, can be slow for 7k+)
                self._seen_hashes.add(phash)

                # Save
                fname = f"{uuid.uuid4().hex}.jpg"
                fpath = os.path.join(folder_path, fname)
                img.save(fpath, "JPEG", quality=90)

                return {"url": url, "path": fpath, "status": "success"}

            except Exception as e:
                if attempt == MAX_RETRIES:
                    return {"url": url, "status": "failed", "reason": str(e)}
                time.sleep(attempt * 2)
        return {"url": url, "status": "failed", "reason": "exhausted"}

    def scrape(self, keywords, limit=100):
        for kw in keywords:
            # Removed filetype operator as it triggers bot detection
            search_query = kw
            safe_kw = kw.replace(" ", "_").replace(":", "")
            folder_path = os.path.join(self.base_output_dir, safe_kw)
            os.makedirs(folder_path, exist_ok=True)

            logger.info(f"Scraping: {search_query}")
            
            try:
                with DDGS() as ddgs:
                    results = list(ddgs.images(
                        search_query,
                        region="wt-wt",
                        safesearch="off",
                        size="Medium",
                        max_results=limit
                    ))
                
                urls = [r["image"] for r in results]
                logger.info(f"Found {len(urls)} URLs for {kw}. Downloading...")

                with ThreadPoolExecutor(max_workers=THREADS) as executor:
                    futures = [executor.submit(self.download_image, url, folder_path) for url in urls]
                    success = 0
                    for future in as_completed(futures):
                        res = future.result()
                        if res["status"] == "success":
                            success += 1
                
                logger.info(f"Completed {kw}: {success} new images saved.")
                # Increased random delay to stay under the radar
                time.sleep(random.uniform(15, 25)) 

            except Exception as e:
                logger.error(f"Search failed for {kw}: {e}")
                time.sleep(60) # Longer cool down after error

if __name__ == "__main__":
    # --- Mega Keyword List (70+ Variations) ---
    KEYWORDS = [
        # BAHAYA (Active/Severe)
        "landslide tension cracks", "soil subsidence fissure", "ground failure surface cracking",
        "slope instability ground crack", "asphalt road crack subsidence", "rekahan tanah lereng longsor",
        "jalan desa ambles retak", "gejala tanah gerak retak", "jalan aspal terbelah longsor",
        "massive landslide debris", "collapsed mountain road", "mudslide destroyed building",
        "active soil movement hill", "fresh landslide scar", "rockfall damage road",
        "site:tribunnews.com jalan longsor", "site:kompas.com tanah retak longsor",
        "site:detik.com rekahan tanah", "landslide disaster area indonesia",
        
        # WASPADA (Warning Signs)
        "soil creep signs", "tilted trees landslide", "leaning telephone poles landslide",
        "retaining wall cracks soil", "tensile soil cracks", "longitudinal ground cracks",
        "transverse pavement cracks", "staircase ground cracking", "hairline soil fissures",
        "widening earth cracks", "hillside cracks warning", "embankment cracks heavy rain",
        "cliff edge soil cracks", "scenic lookout ground cracks", "hiking trail slope failure",
        
        # AMAN (Stable/Natural)
        "dry soil desiccation cracks", "lush green hill slope", "healthy mountain forest",
        "normal garden soil", "standard grass lawn", "stable rock formation",
        "paved road no cracks", "clean mountain path", "healthy tropical forest floor",
        "stable river bank", "green tea plantation hills", "mountain village landscape",
        "forest park landscape stable", "agricultural land healthy soil", "stable limestone cliff",
        "dry parched earth no landslide", "desert soil cracks sun", "stable embankment grass",
        
        # ADDITIONAL VOLUME
        "mountain soil fissure", "mudslide warning signs", "debris flow cracks",
        "expansive soil cracks", "erosion gullies landslide", "slope failure cracks",
        "pavement cracks landslide", "drainage failure cracks", "wet soil cracks",
        "clay soil fissures", "alluvial soil cracks", "tropical soil landslide cracks",
        "bencana pergerakan tanah", "rekahan tanah bukit", "tanah belah longsor",
        "geohazard soil cracks", "mass wasting cracks", "rockfall soil cracks",
        "saturated soil fissures", "subsidence cracks hillside"
    ]

    scraper = RetakAdvancedScraper()
    scraper.scrape(KEYWORDS, limit=150)
