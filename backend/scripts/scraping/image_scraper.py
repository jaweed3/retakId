import os
import requests
import uuid
import json
import logging
from concurrent.futures import ThreadPoolExecutor
from duckduckgo_search import DDGS
from PIL import Image
from io import BytesIO

# Configuration
LOG_FORMAT = "%(asctime)s - %(levelname)s - %(message)s"
logging.basicConfig(level=logging.INFO, format=LOG_FORMAT)
logger = logging.getLogger(__name__)

class RetakScraper:
    def __init__(self, base_output_dir="backend/data/raw"):
        self.base_output_dir = base_output_dir
        if not os.path.exists(self.base_output_dir):
            os.makedirs(self.base_output_dir)

    def download_image(self, url, folder_path):
        """Downloads a single image, validates it, and saves as JPG."""
        try:
            response = requests.get(url, timeout=10)
            response.raise_for_status()
            
            # Validate if it's an image using PIL
            img = Image.open(BytesIO(response.content))
            img = img.convert("RGB") # Ensure consistency
            
            filename = f"{uuid.uuid4().hex}.jpg"
            file_path = os.path.join(folder_path, filename)
            
            img.save(file_path, "JPEG", quality=85)
            return {"url": url, "path": file_path, "status": "success"}
        except Exception as e:
            # logger.debug(f"Failed to download {url}: {e}")
            return {"url": url, "status": "failed", "error": str(e)}

    def scrape_keyword(self, keyword, max_images=100):
        """Scrapes images for a specific keyword."""
        folder_path = os.path.join(self.base_output_dir, keyword.replace(" ", "_"))
        os.makedirs(folder_path, exist_ok=True)
        
        logger.info(f"Searching for '{keyword}'...")
        
        with DDGS() as ddgs:
            results = ddgs.images(
                keyword,
                region="wt-wt",
                safesearch="off",
                size="Medium", # Good balance for training
                max_results=max_images
            )
            
            urls = [r['image'] for r in results]
        
        logger.info(f"Found {len(urls)} candidates. Starting download...")
        
        manifest = []
        with ThreadPoolExecutor(max_workers=10) as executor:
            futures = [executor.submit(self.download_image, url, folder_path) for url in urls]
            for future in futures:
                res = future.result()
                manifest.append(res)
        
        success_count = len([m for m in manifest if m['status'] == 'success'])
        logger.info(f"Successfully downloaded {success_count}/{len(urls)} images for '{keyword}'")
        
        # Save manifest for provenance
        manifest_path = os.path.join(folder_path, "manifest.json")
        with open(manifest_path, "w") as f:
            json.dump(manifest, f, indent=2)

if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="Retak.id Image Scraper")
    parser.add_argument("--keywords", type=str, required=True, help="Comma separated keywords (e.g. 'landslide cracks, soil fissure')")
    parser.add_argument("--limit", type=int, default=100, help="Max images per keyword")
    
    args = parser.parse_args()
    
    scraper = RetakScraper()
    keywords = [k.strip() for k in args.keywords.split(",")]
    
    for kw in keywords:
        scraper.scrape_keyword(kw, max_images=args.limit)
