#!/usr/bin/env bash
# =============================================================================
# Retak.id — Hyperparameter Tuning Runner
# =============================================================================
# Runs multiple config variants sequentially and compares results via MLflow.
#
# Usage:
#   chmod +x scripts/run_experiments.sh
#   ./scripts/run_experiments.sh
#   ./scripts/run_experiments.sh --gpu    # with GPU flag
#
set -euo pipefail

EXPERIMENTS=(
    "backend/config/experiments/v3a_baseline.yaml"
    "backend/config/experiments/v3b_more_layers.yaml"
    "backend/config/experiments/v3c_waspada_boost.yaml"
    "backend/config/experiments/v3d_higher_lr.yaml"
)

PYTHON="uv run --python 3.11"
TRAIN_SCRIPT="backend/src/training/train.py"

echo "============================================"
echo "  Retak.id Hyperparameter Tuning"
echo "  $(date)"
echo "  Experiments: ${#EXPERIMENTS[@]}"
echo "============================================"

BEST_ACC=0
BEST_EXP=""

for exp in "${EXPERIMENTS[@]}"; do
    name=$(basename "$exp" .yaml)
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "  Running: $name"
    echo "  $(date)"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

    $PYTHON $TRAIN_SCRIPT --config backend/config/training.yaml --override "$exp" 2>&1 | tee /tmp/retak_${name}.log

    # Extract accuracy
    ACC=$(grep "Test accuracy:" /tmp/retak_${name}.log | tail -1 | awk '{print $NF}')
    F1_AMAN=$(grep -A3 "f1-score" /tmp/retak_${name}.log | grep "AMAN" | awk '{print $NF}')
    F1_WASPADA=$(grep -A3 "f1-score" /tmp/retak_${name}.log | grep "WASPADA" | awk '{print $NF}')
    F1_BAHAYA=$(grep -A3 "f1-score" /tmp/retak_${name}.log | grep "BAHAYA" | awk '{print $NF}')

    echo ""
    echo "  $name done: acc=$ACC, AMAN_F1=$F1_AMAN, WASPADA_F1=$F1_WASPADA, BAHAYA_F1=$F1_BAHAYA"

    if [ -n "$ACC" ]; then
        ACC_VAL=$(echo "$ACC" | tr -d '%')
        if (( $(echo "$ACC_VAL > $BEST_ACC" | bc -l 2>/dev/null || echo 0) )); then
            BEST_ACC=$ACC_VAL
            BEST_EXP=$name
        fi
    fi
done

echo ""
echo "============================================"
echo "  TUNING COMPLETE"
echo "============================================"
echo "  Best experiment: $BEST_EXP ($BEST_ACC)"
echo ""
echo "  Compare all runs:"
echo "  mlflow ui --backend-store-uri file://$(pwd)/backend/logs/mlruns"
echo "============================================"
