# Retak.id Backend & ML Pipeline

This directory contains the tools and scripts for dataset acquisition and model training.

## Structure
- `data/`: Dataset storage (gitignored).
- `scripts/scraping/`: Selenium/BeautifulSoup scrapers.
- `scripts/processing/`: Data augmentation and cleaning.
- `src/training/`: Transfer learning (MobileNetV2) and TFLite conversion.
- `models/`: Exported model artifacts.

## Quick Start
1. Create venv: `python -m venv venv`
2. Activate: `source venv/bin/activate`
3. Install: `pip install -r requirements.txt`
