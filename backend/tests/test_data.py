"""Tests for data pipeline: loading, shapes, splits, no leakage."""

import os
import tempfile
import shutil
from pathlib import Path

import numpy as np
from PIL import Image


def create_dummy_dataset(base_dir: str, classes: list[str], n_per_class: int = 5):
    """Create a minimal dummy dataset for testing."""
    base = Path(base_dir)
    for cls in classes:
        cls_dir = base / cls
        cls_dir.mkdir(parents=True, exist_ok=True)
        for i in range(n_per_class):
            img = Image.new("RGB", (224, 224), color=(i * 50, 100, 150))
            img.save(cls_dir / f"{cls}_{i}.jpg")


def test_validate_dataset():
    """validate_dataset.py works on a clean dataset."""
    from backend.scripts.processing.validate_dataset import validate_directory

    with tempfile.TemporaryDirectory() as tmpdir:
        create_dummy_dataset(tmpdir, ["AMAN", "WASPADA", "BAHAYA"], n_per_class=3)
        stats = validate_directory(tmpdir)
        assert stats["total"] == 9
        assert stats["valid"] == 9
        assert stats["invalid"] == 0


def test_validate_dataset_invalid():
    """validate_dataset.py catches corrupt files."""
    from backend.scripts.processing.validate_dataset import validate_directory

    with tempfile.TemporaryDirectory() as tmpdir:
        create_dummy_dataset(tmpdir, ["AMAN"], n_per_class=3)
        # Create a corrupt "image"
        corrupt = Path(tmpdir) / "AMAN" / "corrupt.jpg"
        corrupt.write_text("not an image")
        stats = validate_directory(tmpdir)
        assert stats["total"] == 4
        assert stats["valid"] == 3
        assert stats["invalid"] == 1


def test_split_dataset():
    """split_dataset.py produces correct directory structure and counts."""
    from backend.scripts.processing.split_dataset import split_dataset

    with tempfile.TemporaryDirectory() as src:
        with tempfile.TemporaryDirectory() as dst:
            create_dummy_dataset(src, ["AMAN", "WASPADA", "BAHAYA"], n_per_class=10)
            split_dataset(src, dst, train_ratio=0.6, val_ratio=0.2, seed=42)

            for split in ["train", "val", "test"]:
                split_dir = Path(dst) / split
                assert split_dir.exists(), f"{split} dir missing"
                for cls in ["AMAN", "WASPADA", "BAHAYA"]:
                    cls_dir = split_dir / cls
                    assert cls_dir.exists(), f"{split}/{cls} missing"
                    images = list(cls_dir.glob("*.jpg"))
                    assert len(images) > 0, f"No images in {split}/{cls}"

            # Check total across splits equals original
            total = len(list(Path(dst).rglob("*.jpg")))
            assert total == 30, f"Expected 30 images, got {total}"


def test_split_reproducibility():
    """Same seed produces same split."""
    from backend.scripts.processing.split_dataset import split_dataset

    with tempfile.TemporaryDirectory() as src:
        create_dummy_dataset(src, ["AMAN", "WASPADA"], n_per_class=10)

        dst1 = tempfile.mkdtemp()
        dst2 = tempfile.mkdtemp()
        try:
            split_dataset(src, dst1, train_ratio=0.6, val_ratio=0.2, seed=42)
            split_dataset(src, dst2, train_ratio=0.6, val_ratio=0.2, seed=42)

            # Compare file lists
            files1 = sorted(Path(dst1).rglob("*.jpg"))
            files2 = sorted(Path(dst2).rglob("*.jpg"))
            names1 = [f.name for f in files1]
            names2 = [f.name for f in files2]
            assert names1 == names2, "Reproducibility broken"
        finally:
            shutil.rmtree(dst1, ignore_errors=True)
            shutil.rmtree(dst2, ignore_errors=True)


def test_dataset_stats():
    """dataset_stats.py returns correct structure."""
    from backend.scripts.processing.dataset_stats import compute_stats

    with tempfile.TemporaryDirectory() as tmpdir:
        create_dummy_dataset(tmpdir, ["AMAN", "WASPADA"], n_per_class=5)
        stats = compute_stats(tmpdir)
        assert stats["total_images"] == 10
        assert stats["classes"] == 2
        assert stats["class_distribution"]["AMAN"] == 5
        assert stats["class_distribution"]["WASPADA"] == 5
        assert "per_class" in stats
        assert "AMAN" in stats["per_class"]
        assert stats["per_class"]["AMAN"]["count"] == 5


def test_deduplicate_no_dupes():
    """deduplicate.py finds nothing in clean dataset."""
    from backend.scripts.processing.deduplicate import find_cross_class_duplicates

    with tempfile.TemporaryDirectory() as tmpdir:
        create_dummy_dataset(tmpdir, ["AMAN", "WASPADA"], n_per_class=5)
        dupes = find_cross_class_duplicates(tmpdir, hamming_threshold=3)
        # Different solid colors -> different pHash -> no dupes
        assert len(dupes) == 0


def test_config_loader():
    """config_loader loads and validates config."""
    from backend.src.training.config_loader import load_config

    config = load_config("backend/config/training.yaml")
    assert config.model.base == "mobilenetv2"
    assert config.model.num_classes == 3
    assert config.model.dropout == 0.3
    assert config.training.learning_rate == 0.0001
    assert config.export.class_labels == ["AMAN", "WASPADA", "BAHAYA"]
