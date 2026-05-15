"""Download SRTM HGT tile for Jenangan, Ponorogo (-7.876, 111.464)."""

import argparse
import os
import sys
import urllib.request
import zipfile

TILE = "S08E111"

SOURCES = [
    f"https://srtm.csi.cgiar.org/wp-content/uploads/files/srtm_5x5/TIFF/srtm_61_14.zip",
    f"https://srtm.csi.cgiar.org/SRT-ZIP/SRTM_V41/SRTM_Data_GeoTiff/srtm_61_14.zip",
]

OUTPUT_DIR = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "mobile-app", "app", "src", "main", "assets", "dem"
)

def find_tile_in_zip(zip_path: str, tile_name: str) -> bool:
    with zipfile.ZipFile(zip_path) as zf:
        for name in zf.namelist():
            if tile_name in name and name.endswith(".hgt"):
                print(f"  Found {name} in zip, extracting...")
                zf.extract(name, OUTPUT_DIR)
                extracted_path = os.path.join(OUTPUT_DIR, name)
                final_path = os.path.join(OUTPUT_DIR, f"{tile_name}.hgt")
                if extracted_path != final_path:
                    os.rename(extracted_path, final_path)
                return True
    return False

def main():
    parser = argparse.ArgumentParser(description="Download SRTM tile")
    parser.add_argument("--tile", default=TILE, help=f"Tile name (default: {TILE})")
    parser.add_argument("--output", default=OUTPUT_DIR, help="Output directory")
    args = parser.parse_args()

    os.makedirs(args.output, exist_ok=True)
    dest = os.path.join(args.output, f"{args.tile}.hgt")

    if os.path.exists(dest):
        size_kb = os.path.getsize(dest) / 1024
        print(f"Tile already exists: {dest} ({size_kb:.0f} KB)")
        return

    zip_path = os.path.join(args.output, "temp.zip")

    for url in SOURCES:
        print(f"Trying: {url}")
        try:
            urllib.request.urlretrieve(url, zip_path)
            print("  Downloaded zip, searching for tile...")
            if find_tile_in_zip(zip_path, args.tile):
                os.remove(zip_path)
                size_kb = os.path.getsize(dest) / 1024
                print(f"Success! Tile saved to: {dest} ({size_kb:.0f} KB)")
                return
            os.remove(zip_path)
        except Exception as e:
            print(f"  Failed: {e}")
            if os.path.exists(zip_path):
                os.remove(zip_path)

    print("ERROR: Could not download tile from any source.")
    print("Manual download: https://earthexplorer.usgs.gov/")
    print(f"Then save file as: {dest}")
    sys.exit(1)

if __name__ == "__main__":
    main()
