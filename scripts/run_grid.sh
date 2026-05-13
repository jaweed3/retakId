#!/usr/bin/env bash
# =============================================================================
# Retak.id — Grid Search Runner (with resume support)
# =============================================================================
# Runs all generated grid experiments. Skips already-completed runs.
# If SSH disconnects, just re-run — it'll pick up where it left off.
#
# Usage:
#   bash scripts/run_grid.sh              # run all, skip completed
#   bash scripts/run_grid.sh --force      # re-run ALL (ignore resume)
#   bash scripts/run_grid.sh --dry-run    # list what would run
#
set -euo pipefail

FORCE=false
DRY_RUN=false

for arg in "$@"; do
    case $arg in
        --force) FORCE=true ;;
        --dry-run) DRY_RUN=true ;;
    esac
done

EXPERIMENTS_DIR="backend/config/experiments"
PYTHON="uv run --python 3.11"
TRAIN_SCRIPT="backend/src/training/train.py"
BASE_CONFIG="backend/config/training.yaml"
MLRUNS_DIR="backend/logs/mlruns"

# Count total
TOTAL=$(ls "$EXPERIMENTS_DIR"/grid_*.yaml 2>/dev/null | wc -l)
if [ "$TOTAL" -eq 0 ]; then
    echo "No grid experiments found. Run 'make grid-gen' first."
    exit 1
fi

echo "============================================"
echo "  Retak.id Grid Search"
echo "  $(date)"
echo "  Experiments: $TOTAL"
echo "  Force: $FORCE | Dry-run: $DRY_RUN"
echo "============================================"

RUN=0
SKIPPED=0
FAILED=0
BEST_ACC=0
BEST_EXP=""

for exp in "$EXPERIMENTS_DIR"/grid_*.yaml; do
    name=$(basename "$exp" .yaml)

    # --- Resume check ---
    # Check if this experiment already has an MLflow run by searching param hash
    if [ "$FORCE" = false ] && [ -d "$MLRUNS_DIR" ]; then
        # Simple check: look for completed marker file
        MARKER="/tmp/retak_grid_done_${name}"
        if [ -f "$MARKER" ]; then
            SKIPPED=$((SKIPPED + 1))
            echo "  [SKIP] $name — already completed"
            continue
        fi
    fi

    if [ "$DRY_RUN" = true ]; then
        echo "  [DRY-RUN] $name"
        # Show key params
        grep -E "learning_rate|fine_tune_at|dropout|class_weight|rotation_range" "$exp" 2>/dev/null | head -5 | sed 's/^/    /'
        RUN=$((RUN + 1))
        continue
    fi

    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "  [$((RUN + 1))/$TOTAL] $name"
    echo "  $(date)"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

    if $PYTHON $TRAIN_SCRIPT --config "$BASE_CONFIG" --override "$exp" 2>&1 | tee "/tmp/retak_${name}.log"; then
        # Extract accuracy
        ACC=$(grep "Test accuracy:" "/tmp/retak_${name}.log" | tail -1 | awk '{print $NF}' || echo "0")
        echo "  ✓ $name — accuracy: $ACC"

        # Mark as done
        touch "/tmp/retak_grid_done_${name}"

        # Track best
        if [ -n "$ACC" ]; then
            ACC_VAL=$(echo "$ACC" | bc -l 2>/dev/null || echo "0")
            if (( $(echo "$ACC_VAL > $BEST_ACC" | bc -l 2>/dev/null) )); then
                BEST_ACC=$ACC_VAL
                BEST_EXP=$name
            fi
        fi
        RUN=$((RUN + 1))
    else
        echo "  ✗ $name FAILED"
        FAILED=$((FAILED + 1))
    fi
done

echo ""
echo "============================================"
echo "  GRID SEARCH COMPLETE"
echo "============================================"
echo "  Total:    $TOTAL"
echo "  Run:      $RUN"
echo "  Skipped:  $SKIPPED"
echo "  Failed:   $FAILED"
echo "  Best:     $BEST_EXP ($BEST_ACC)"
echo ""
echo "  Compare:"
echo "  mlflow ui --backend-store-uri file://$(pwd)/$MLRUNS_DIR"
echo ""
echo "  Re-run failed:"
echo "  bash scripts/run_grid.sh --force"
echo "============================================"
