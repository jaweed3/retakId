import os
import time
import uuid
import logging
import random
import threading
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

MIN_WIDTH = 400
MIN_HEIGHT = 400
MIN_FILE_SIZE = 100 * 1024  # 100KB
MAX_TARGET = 2000
BLUR_THRESHOLD = 90.0
THREADS = 8 # Reduced to avoid overwhelming
MAX_RETRIES = 3

class AmanDatasetScraper:
    def __init__(self, output_dir="backend/data/raw/AMAN"):
        self.output_dir = output_dir
        os.makedirs(self.output_dir, exist_ok=True)
        
        self.ua = UserAgent()
        self._seen_hashes = set()
        self._total_success = 0
        self._lock = threading.Lock()
        
        self._load_existing()

    def _load_existing(self):
        logger.info(f"Scanning {self.output_dir} for existing data...")
        files = [f for f in os.listdir(self.output_dir) if f.lower().endswith(('.jpg', '.jpeg'))]
        self._total_success = len(files)
        logger.info(f"Found {self._total_success} existing images.")

    def _get_headers(self):
        return {
            "User-Agent": self.ua.random,
            "Accept": "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8",
            "Referer": "https://www.google.com/",
        }

    def _validate_image(self, img, raw_content):
        size = len(raw_content)
        if size < MIN_FILE_SIZE:
            return False, f"too_small_kb_{size//1024}"

        w, h = img.size
        if w < MIN_WIDTH or h < MIN_HEIGHT:
            return False, f"too_small_dim_{w}x{h}"
        
        ratio = max(w, h) / min(w, h)
        if ratio > 1.8:
            return False, f"bad_aspect_{ratio:.1f}"

        gray = img.convert("L")
        arr = np.array(gray, dtype=np.float64)
        laplacian = np.abs(arr[1:-1, 1:-1] * -4 + arr[0:-2, 1:-1] + arr[2:, 1:-1] + arr[1:-1, 0:-2] + arr[1:-1, 2:])
        score = laplacian.var()
        if score < BLUR_THRESHOLD:
            return False, f"blurry_{score:.1f}"
        
        return True, "ok"

    def download_image(self, url):
        with self._lock:
            if self._total_success >= MAX_TARGET:
                return {"status": "target_reached"}

        time.sleep(random.uniform(1.0, 3.0)) # Politeness
        
        for attempt in range(1, MAX_RETRIES + 1):
            try:
                headers = self._get_headers()
                response = requests.get(url, headers=headers, timeout=15)
                response.raise_for_status()

                content = response.content
                img = Image.open(BytesIO(content))
                img = img.convert("RGB")

                ok, reason = self._validate_image(img, content)
                if not ok:
                    return {"url": url, "status": "rejected", "reason": reason}

                phash = str(imagehash.phash(img))
                with self._lock:
                    if phash in self._seen_hashes:
                        return {"url": url, "status": "rejected", "reason": "duplicate"}
                    self._seen_hashes.add(phash)
                    
                    if self._total_success >= MAX_TARGET:
                        return {"status": "target_reached"}
                    
                    self._total_success += 1
                    current_count = self._total_success

                fname = f"aman_{uuid.uuid4().hex[:10]}.jpg"
                fpath = os.path.join(self.output_dir, fname)
                img.save(fpath, "JPEG", quality=90)

                if current_count % 10 == 0:
                    logger.info(f"Progress: {current_count}/{MAX_TARGET}")

                return {"url": url, "status": "success"}

            except Exception:
                time.sleep(attempt * 2)
        
        return {"url": url, "status": "failed"}

    def run(self, keywords):
        logger.info(f"AMAN Target: {MAX_TARGET}")
        
        with DDGS() as ddgs:
            for kw in keywords:
                with self._lock:
                    if self._total_success >= MAX_TARGET:
                        break
                
                logger.info(f"Keyword: {kw}")
                try:
                    results = list(ddgs.images(
                        kw,
                        region="wt-wt",
                        safesearch="off",
                        max_results=150
                    ))
                    
                    if not results:
                        logger.warning(f"No results for {kw}")
                        continue

                    urls = [r["image"] for r in results]
                    random.shuffle(urls)

                    with ThreadPoolExecutor(max_workers=THREADS) as executor:
                        futures = [executor.submit(self.download_image, url) for url in urls]
                        for future in as_completed(futures):
                            res = future.result()
                            if res.get("status") == "target_reached":
                                logger.info("Target reached!")
                                return

                    time.sleep(random.uniform(10, 20))

                except Exception as e:
                    logger.error(f"Error: {e}")
                    time.sleep(30)

        logger.info(f"Finished. Total: {self._total_success}/{MAX_TARGET}")

if __name__ == "__main__":
    AMAN_KEYWORDS = [
        "normal soil surface texture close up",
        "intact dirt road surface",
        "healthy agricultural field soil",
        "forest floor soil texture",
        "compacted bare ground close up",
        "permukaan tanah padat utuh",
        "dry soil desiccation cracks close up",
        "mud cracks drought texture",
        "minor surface soil shrinkage",
        "tanah kering retak kemarau",
        "ground texture photography soil",
        "flat earth surface close up",
        "unbroken clay soil texture",
        "stable garden earth surface",
        "natural soil path texture"
    ]

    scraper = AmanDatasetScraper()
    scraper.run(AMAN_KEYWORDS)
