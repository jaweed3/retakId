.PHONY: setup scrape train clean

setup:
	uv sync

# Usage: make scrape KW="landslide cracks, soil fissure" LIMIT=100
scrape:
	uv run python backend/scripts/scraping/image_scraper.py --keywords "$(KW)" --limit $(LIMIT)

train:
	uv run python backend/src/training/train.py

clean:
	find . -type d -name "__pycache__" -exec rm -rf {} +
	rm -rf .uv
	rm -rf venv
