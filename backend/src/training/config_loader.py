"""Configuration loader for Retak.id training pipeline.

Loads YAML config, validates required keys, applies defaults, and returns
a typed namespace for use throughout the training pipeline.

Usage:
    from backend.src.training.config_loader import load_config
    config = load_config("backend/config/training.yaml")
    print(config.model.base)  # "mobilenetv2"
"""

import os
from pathlib import Path
from typing import Any

import yaml


class _ConfigNamespace:
    """Recursively convert dict to attribute-accessible namespace."""

    def __init__(self, data: dict):
        for key, value in data.items():
            if isinstance(value, dict):
                setattr(self, key, _ConfigNamespace(value))
            elif isinstance(value, list):
                setattr(self, key, value)
            else:
                setattr(self, key, value)

    def to_dict(self) -> dict:
        result = {}
        for key, value in self.__dict__.items():
            if isinstance(value, _ConfigNamespace):
                result[key] = value.to_dict()
            else:
                result[key] = value
        return result

    def __repr__(self) -> str:
        return f"Config({self.to_dict()})"


def _validate_config(data: dict) -> None:
    """Validate required top-level keys exist."""
    required = ["data", "model", "training", "augmentation", "quantization", "export", "logging"]
    missing = [k for k in required if k not in data]
    if missing:
        raise KeyError(f"Missing required config sections: {missing}")

    required_model = ["base", "weights", "num_classes", "dropout"]
    missing_model = [k for k in required_model if k not in data.get("model", {})]
    if missing_model:
        raise KeyError(f"Missing required model config keys: {missing_model}")

    required_training = ["optimizer", "learning_rate", "loss", "epochs"]
    missing_training = [k for k in required_training if k not in data.get("training", {})]
    if missing_training:
        raise KeyError(f"Missing required training config keys: {missing_training}")


def load_config(config_path: str) -> _ConfigNamespace:
    """Load and validate training configuration from YAML.

    Args:
        config_path: Path to training.yaml file.

    Returns:
        _ConfigNamespace with attribute-accessible configuration.

    Raises:
        FileNotFoundError: If config file doesn't exist.
        KeyError: If required keys are missing.
    """
    path = Path(config_path)
    if not path.exists():
        raise FileNotFoundError(f"Config file not found: {config_path}")

    with open(path, "r") as f:
        data = yaml.safe_load(f)

    _validate_config(data)

    # Resolve relative paths relative to project root (2 levels up from config/)
    project_root = path.parent.parent.parent
    for key in ["processed_dir", "splits_dir"]:
        if key in data.get("data", {}):
            p = Path(data["data"][key])
            if not p.is_absolute():
                data["data"][key] = str(project_root / p)
    for key in ["tensorboard_dir", "checkpoint_dir", "runs_csv"]:
        if key in data.get("logging", {}):
            p = Path(data["logging"][key])
            if not p.is_absolute():
                data["logging"][key] = str(project_root / p)
    for key in ["output_dir"]:
        if key in data.get("export", {}):
            p = Path(data["export"][key])
            if not p.is_absolute():
                data["export"][key] = str(project_root / p)

    return _ConfigNamespace(data)


def load_config_dict(config_path: str) -> dict:
    """Load config as a plain dict without namespace wrapping.

    Useful for passing to functions that expect dicts.
    """
    ns = load_config(config_path)
    return ns.to_dict()
