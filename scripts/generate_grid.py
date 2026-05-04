#!/usr/bin/env python3
"""Generate experiment config files from grid_search.yaml.

Usage:
    uv run python scripts/generate_grid.py
    uv run python scripts/generate_grid.py --grid backend/config/grid_search.yaml
    uv run python scripts/generate_grid.py --dry-run     # just count, don't write

Output:
    backend/config/experiments/grid_NNNN.yaml  (one per combination)
"""

import os
import sys
import itertools
import argparse
from pathlib import Path
import yaml


def set_nested(d: dict, key_path: str, value):
    """Set a nested dict value by dot-separated key path."""
    keys = key_path.split(".")
    for key in keys[:-1]:
        d = d.setdefault(key, {})
    d[keys[-1]] = value


def generate(grid_path: str, output_dir: str, dry_run: bool = False):
    # Load grid
    with open(grid_path) as f:
        grid = yaml.safe_load(f)

    parameters = grid["parameters"]
    fixed = grid.get("fixed", {})
    excludes = grid.get("exclude", [])

    # Build all combinations
    param_names = list(parameters.keys())
    param_values = [parameters[name] for name in param_names]

    os.makedirs(output_dir, exist_ok=True)

    # Clean old grid configs
    for old in Path(output_dir).glob("grid_*.yaml"):
        if not dry_run:
            old.unlink()

    count = 0
    skipped = 0
    for combo in itertools.product(*param_values):
        # Build override dict
        override = dict(fixed)
        for name, value in zip(param_names, combo):
            set_nested(override, name, value)

        # Check excludes
        skip = False
        for ex in excludes:
            if all(
                override.get(k.split(".")[-1]) == v
                if "." not in k
                else _get_nested(override, k) == v
                for k, v in ex.items()
            ):
                skip = True
                break

        if skip:
            skipped += 1
            continue

        if dry_run:
            count += 1
            continue

        # Write override file
        fname = f"grid_{count:04d}.yaml"
        fpath = os.path.join(output_dir, fname)
        with open(fpath, "w") as f:
            yaml.dump(override, f, default_flow_style=False, allow_unicode=True)

        count += 1

    print(f"Generated {count} experiment configs (skipped {skipped} excluded)")
    print(f"Output: {output_dir}/grid_*.yaml")

    if dry_run:
        print("DRY RUN — no files written")


def _get_nested(d: dict, key_path: str):
    """Get a nested dict value by dot-separated key path."""
    keys = key_path.split(".")
    for key in keys:
        d = d.get(key, {})
    return d


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Generate grid search configs")
    parser.add_argument("--grid", type=str,
                        default="backend/config/grid_search.yaml")
    parser.add_argument("--output", type=str,
                        default="backend/config/experiments")
    parser.add_argument("--dry-run", action="store_true",
                        help="Just count, don't write files")
    args = parser.parse_args()

    generate(args.grid, args.output, args.dry_run)
