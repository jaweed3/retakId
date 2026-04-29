# Getting Started

## Prerequisites
- Python 3.10+
- [uv](https://github.com/astral-sh/uv) installed.
- Android Studio (for app development).

## Backend Setup
1. Clone the repository.
2. Run `make setup` to install dependencies using `uv`.
3. To start scraping: `make scrape`.
4. To train the model: `make train`.

## Model Integration
After training, the `.tflite` model and `labels.txt` should be placed in the Android app's `assets/` directory.
