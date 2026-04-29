.PHONY: setup scrape train evaluate export test clean clean-logs lint

PYTHON = uv run --python 3.11
CONFIG = backend/config/training.yaml

setup:
	uv sync --python 3.11

# --- Data Pipeline ---

# Usage: make scrape KW="landslide cracks, soil fissure" LIMIT=100
scrape:
	$(PYTHON) backend/scripts/scraping/image_scraper.py --keywords "$(KW)" --limit $(LIMIT)

validate:
	$(PYTHON) backend/scripts/processing/validate_dataset.py --data-dir backend/data/processed

deduplicate:
	$(PYTHON) backend/scripts/processing/deduplicate.py --data-dir backend/data/processed

stats:
	$(PYTHON) backend/scripts/processing/dataset_stats.py --data-dir backend/data/processed

split:
	$(PYTHON) backend/scripts/processing/split_dataset.py --data-dir backend/data/processed --output-dir backend/data/splits

# --- Training Pipeline ---

train:
	$(PYTHON) backend/src/training/train.py --config $(CONFIG)

evaluate:
	$(PYTHON) -c "\
	from backend.src.training.config_loader import load_config; \
	from backend.src.training.evaluation import evaluate_model, plot_training_history; \
	from backend.src.training.train import load_datasets; \
	import tensorflow as tf; \
	config = load_config('$(CONFIG)'); \
	_, _, test_ds = load_datasets(config); \
	model = tf.keras.models.load_model('backend/models/checkpoints/best.keras'); \
	evaluate_model(model, test_ds, config.export.class_labels, output_dir='backend/logs')"

# Export TFLite from a trained model checkpoint
# Usage: make export MODEL=backend/models/checkpoints/best.keras
export:
	$(PYTHON) backend/src/training/export.py --model-path $(MODEL) --config $(CONFIG)

# --- Testing ---

test:
	$(PYTHON) -m pytest backend/tests/ -v

# --- Docker ---

docker-build:
	docker build -t retakid-train -f backend/Dockerfile .

docker-train:
	docker run -v $(PWD)/backend/data:/app/backend/data -v $(PWD)/backend/models:/app/backend/models -v $(PWD)/backend/logs:/app/backend/logs retakid-train

# --- Utilities ---

lint:
	$(PYTHON) -m black --check backend/

format:
	$(PYTHON) -m black backend/

clean:
	find . -type d -name "__pycache__" -exec rm -rf {} +
	rm -rf .uv
	rm -rf venv

clean-logs:
	rm -rf backend/logs/tensorboard/*
	rm -rf backend/logs/runs.csv
	rm -rf backend/logs/*.csv
	rm -rf backend/logs/*.png
