#!/usr/bin/env bash
# =============================================================================
# Retak.id — Lab PC Bootstrap Script
# =============================================================================
# Satu perintah untuk setup SEMUA di PC lab tanpa install dependency manual.
#
# Cara pakai:
#   chmod +x scripts/bootstrap.sh
#   ./scripts/bootstrap.sh
#
# Atau langsung dari GitHub (clone dulu):
#   git clone https://github.com/jaweed3/retakId.git && cd retakId
#   bash scripts/bootstrap.sh
#
# Yang dilakukan script ini:
#   1. Install uv (Python package manager) — zero system dependency
#   2. Install Python 3.11 via uv (standalone, ga ganggu system Python)
#   3. Install semua dependency (TensorFlow, DVC, OpenCV, dll)
#   4. Pull dataset dari DagsHub via DVC
#   5. Siap training
# =============================================================================

set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

banner() {
    echo ""
    echo -e "${CYAN}${BOLD}═══════════════════════════════════════════════════════${NC}"
    echo -e "${CYAN}${BOLD}  Retak.id — Lab Training Setup${NC}"
    echo -e "${CYAN}${BOLD}═══════════════════════════════════════════════════════${NC}"
    echo ""
}

info()  { echo -e "${GREEN}[✓]${NC} $1"; }
warn()  { echo -e "${YELLOW}[!]${NC} $1"; }
fail()  { echo -e "${RED}[✗]${NC} $1"; exit 1; }
step()  { echo -e "\n${BOLD}── $1 ──${NC}"; }

# ─── Main ───────────────────────────────────────────────────────────────────

banner

# 1. Install uv
step "Step 1/4: Installing uv (package manager)"
if command -v uv &>/dev/null; then
    info "uv already installed: $(uv --version)"
else
    echo "Downloading uv installer..."
    if curl -LsSf https://astral.sh/uv/install.sh | sh; then
        # Add to PATH for this session
        export PATH="$HOME/.local/bin:$HOME/.cargo/bin:$PATH"
        info "uv installed: $(uv --version)"
    else
        fail "Failed to install uv. Coba manual: https://docs.astral.sh/uv/getting-started/installation/"
    fi
fi

# 2. Install Python 3.11 via uv
step "Step 2/4: Installing Python 3.11 (standalone, ga ganggu system)"
PYTHON_311=$(uv python find 3.11 2>/dev/null || echo "")
if [ -n "$PYTHON_311" ]; then
    info "Python 3.11 already available: $($PYTHON_311 --version)"
else
    echo "Downloading Python 3.11 (this may take a minute)..."
    if uv python install 3.11; then
        PYTHON_311=$(uv python find 3.11)
        info "Python 3.11 installed: $($PYTHON_311 --version)"
    else
        fail "Failed to install Python 3.11 via uv"
    fi
fi

# 3. Install project dependencies
step "Step 3/4: Installing dependencies (TensorFlow, DVC, OpenCV, etc)"
echo "This will download ~2GB of packages. Sabar ya..."
if uv sync --python 3.11; then
    info "All dependencies installed"
else
    fail "Failed to install dependencies"
fi

# 4. Pull dataset from DagsHub via DVC
step "Step 4/4: Pulling dataset from DagsHub DVC"
if uv run --python 3.11 dvc pull 2>&1; then
    info "Dataset pulled successfully"
else
    warn "DVC pull failed (mungkin perlu auth token DagsHub)"
    echo ""
    echo "  Kalo repo DagsHub public, ini harusnya jalan otomatis."
    echo "  Kalo private, setup auth dulu:"
    echo ""
    echo "    1. Buka https://dagshub.com/user/settings/tokens"
    echo "    2. Create token (scope: dvc)"
    echo "    3. Jalankan:"
    echo "       uv run dvc remote modify dagshub --local auth basic"
    echo "       uv run dvc remote modify dagshub --local user <username>"
    echo "       uv run dvc remote modify dagshub --local password <token>"
    echo "       uv run dvc pull"
    echo ""
fi

# ─── Done ───────────────────────────────────────────────────────────────────

echo ""
echo -e "${CYAN}${BOLD}═══════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}${BOLD}  SETUP COMPLETE! ✓${NC}"
echo -e "${CYAN}${BOLD}═══════════════════════════════════════════════════════${NC}"
echo ""
echo -e "  ${BOLD}Dataset location:${NC}  backend/data/processed/"
echo -e "  ${BOLD}Config:${NC}           backend/config/training.yaml"
echo ""
echo -e "  ${BOLD}Mulai training:${NC}"
echo -e "    ${CYAN}make train${NC}"
echo ""
echo -e "  ${BOLD}Monitor training:${NC}"
echo -e "    ${CYAN}tensorboard --logdir backend/logs/tensorboard${NC}"
echo ""
echo -e "  ${BOLD}Habis training, deploy ke app:${NC}"
echo -e "    ${CYAN}make deploy${NC}"
echo ""
echo "  Selamat menambang akurasi! 🚀"
echo ""
