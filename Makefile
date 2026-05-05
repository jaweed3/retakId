.PHONY: setup scrape validate deduplicate stats split train tune grid-gen grid-run cv register list-models deploy-registered evaluate export deploy test docker-build docker-train lab-setup pull-data lint format clean clean-logs

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

# --- Hyperparameter Tuning ---

# Generate experiment configs from grid
grid-gen:
	$(PYTHON) scripts/generate_grid.py
	@echo "Grid configs generated. Run 'make grid-run' to start."

# Run all grid experiments (with resume support — re-run if SSH disconnects)
grid-run:
	bash scripts/run_grid.sh

# Dry-run: show experiment count without running
grid-dry:
	$(PYTHON) scripts/generate_grid.py --dry-run

# Full tuning pipeline: generate + run
tune: grid-gen grid-run

# Run all experiment variants (legacy)
tune-legacy:
	bash scripts/run_experiments.sh

# Run single experiment variant
# Usage: make train-exp EXP=backend/config/experiments/v3b_more_layers.yaml
train-exp:
	$(PYTHON) backend/src/training/train.py --config backend/config/training.yaml --override $(EXP)

# Export TFLite from a trained model checkpoint
# Usage: make export MODEL=backend/models/checkpoints/best.keras
export:
	$(PYTHON) backend/src/training/export.py --model-path $(MODEL) --config $(CONFIG)

# --- Model Registry ---

# Cross-validate best config (k=5 folds)
cv:
	$(PYTHON) scripts/cross_validate.py --config $(CONFIG)

# Evaluate + register model to MLflow Registry
# Usage: make register RUN_ID=<mlflow_run_id>
register:
	$(PYTHON) scripts/register_model.py --run-id $(RUN_ID)

# List registered models
list-models:
	$(PYTHON) scripts/register_model.py --list

# Download best registered model for Android
# Usage: make deploy-registered ASSETS=app/src/main/assets
deploy-registered:
	$(PYTHON) scripts/register_model.py --download --output $(ASSETS)

# --- Deployment ---

# Copy model artifacts to Android assets directory
# Usage: make deploy ASSETS=app/src/main/assets
ASSETS ?= app/src/main/assets
deploy:
	mkdir -p $(ASSETS)
	cp backend/models/retak_mobilenetv2.tflite $(ASSETS)/
	cp backend/models/labels.txt $(ASSETS)/
	@echo "Deployed model to $(ASSETS)/"

# --- Testing ---

test:
	$(PYTHON) -m pytest backend/tests/ -v

# --- Docker ---

docker-build:
	docker build -t retakid-train -f backend/Dockerfile .

docker-train:
	docker run -v $(PWD)/backend/data:/app/backend/data -v $(PWD)/backend/models:/app/backend/models -v $(PWD)/backend/logs:/app/backend/logs retakid-train

# --- Lab PC ---

# Bootstrap fresh PC: install uv + Python 3.11 + deps + pull data
# Single command for lab computers
lab-setup:
	bash scripts/bootstrap.sh

# Pull dataset from DagsHub via DVC
pull-data:
	$(PYTHON) dvc pull

# Full pipeline from zero: setup → pull data → check → split → train
lab-train: pull-data validate deduplicate split train
	@echo ""
	@echo "Lab training complete! Model di backend/models/"

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
