"""Generate all charts for Retak.id pitch deck."""

import matplotlib

matplotlib.use("Agg")

import matplotlib.pyplot as plt
import matplotlib.ticker as mticker
import numpy as np
from pathlib import Path

OUT = Path(__file__).parent / "charts"
OUT.mkdir(parents=True, exist_ok=True)

plt.rcParams.update({
    "font.family": "sans-serif",
    "font.sans-serif": ["DejaVu Sans"],
    "font.size": 12,
    "axes.titlesize": 14,
    "axes.labelsize": 12,
    "figure.dpi": 200,
})


# ── Chart 1: Model Size Comparison ──
def chart_model_size():
    labels = ["Full Model\n(INT8)", "Delta .rkd\n(v3a→v3b)"]
    sizes_mb = [2.6, 0.047]
    colors = ["#E53935", "#43A047"]

    fig, ax = plt.subplots(figsize=(5, 4))
    bars = ax.bar(labels, sizes_mb, color=colors, width=0.5, edgecolor="white", linewidth=1.5)

    for bar, val in zip(bars, sizes_mb):
        ax.text(bar.get_x() + bar.get_width() / 2, bar.get_height() + 0.05,
                f"{val:.2f} MB" if val >= 0.01 else f"{val*1000:.0f} KB",
                ha="center", va="bottom", fontsize=13, fontweight="bold")

    ax.set_ylabel("Size (MB)")
    ax.set_ylim(0, 3.5)
    ax.yaxis.set_major_formatter(mticker.FormatStrFormatter("%.1f"))
    ax.spines["top"].set_visible(False)
    ax.spines["right"].set_visible(False)

    # annotation
    ax.annotate("98.2% smaller", xy=(1, 0.047), xytext=(1.2, 1.2),
                ha="center", fontsize=11, color="#43A047", fontweight="bold",
                arrowprops=dict(arrowstyle="->", color="#43A047", lw=2))

    fig.tight_layout()
    fig.savefig(OUT / "model_size.png")
    plt.close(fig)
    print("  ✓ model_size.png")


# ── Chart 2: Inference Performance by Device Tier ──
def chart_inference_perf():
    tiers = ["Low-end\n(SD 425)", "Mid-range\n(SD 665)", "High-end\n(SD 8 Gen1)"]
    times_ms = [2800, 450, 150]
    colors = ["#FF7043", "#FFA726", "#66BB6A"]

    fig, ax = plt.subplots(figsize=(5, 4))
    bars = ax.bar(tiers, times_ms, color=colors, width=0.5, edgecolor="white", linewidth=1.5)

    for bar, val in zip(bars, times_ms):
        label = f"{val} ms" if val < 1000 else f"{val/1000:.1f} s"
        ax.text(bar.get_x() + bar.get_width() / 2, bar.get_height() + 30,
                label, ha="center", va="bottom", fontsize=12, fontweight="bold")

    ax.set_ylabel("Inference Time")
    ax.set_ylim(0, 3500)
    ax.spines["top"].set_visible(False)
    ax.spines["right"].set_visible(False)

    fig.tight_layout()
    fig.savefig(OUT / "inference_perf.png")
    plt.close(fig)
    print("  ✓ inference_perf.png")


# ── Chart 3: Risk Factor Weights (Horizontal Bar) ──
def chart_risk_weights():
    factors = ["ML Prediction", "Slope\n(Kemiringan)", "Rain\n(Curah Hujan)",
               "Elevation\n(Elevasi)", "Soil\n(Jenis Tanah)"]
    weights = [50, 20, 15, 10, 5]
    colors = ["#C62828", "#E53935", "#FB8C00", "#43A047", "#1565C0"]

    fig, ax = plt.subplots(figsize=(6.5, 3))
    bars = ax.barh(factors, weights, color=colors, height=0.6, edgecolor="white", linewidth=1.5)

    for bar, val in zip(bars, weights):
        ax.text(bar.get_width() + 0.5, bar.get_y() + bar.get_height() / 2,
                f"{val}%", ha="left", va="center", fontsize=12, fontweight="bold")

    ax.set_xlabel("Weight (%)")
    ax.set_xlim(0, 65)
    ax.spines["top"].set_visible(False)
    ax.spines["right"].set_visible(False)
    ax.invert_yaxis()

    fig.tight_layout()
    fig.savefig(OUT / "risk_weights.png")
    plt.close(fig)
    print("  ✓ risk_weights.png")


# ── Chart 4: Delta Savings Breakdown (Waterfall-ish) ──
def chart_delta_savings():
    stages = ["Full\nModel", "Changed\nBytes", "Gzip\nCompress", "Delta\n.rkd"]
    values = [2.6, -2.55, -0.003, 0.047]
    colors = ["#E53935", "#EF5350", "#FF8A65", "#43A047"]

    fig, ax = plt.subplots(figsize=(5, 4))
    cumulative = 0
    for i, (stage, val, color) in enumerate(zip(stages, values, colors)):
        bottom = cumulative if val >= 0 else cumulative + val
        height = abs(val)
        ax.bar(i, height, bottom=min(cumulative, cumulative + val),
               color=color, width=0.5, edgecolor="white", linewidth=1.5)

        mid = cumulative + val / 2
        ax.text(i, mid, f"{abs(val):.2f} MB" if abs(val) >= 0.001 else f"{abs(val)*1000:.0f} KB",
                ha="center", va="center", fontsize=10, fontweight="bold", color="white")
        cumulative += val

    ax.set_xticks(range(len(stages)))
    ax.set_xticklabels(stages)
    ax.set_ylabel("Size (MB)")
    ax.set_ylim(0, 3.5)
    ax.spines["top"].set_visible(False)
    ax.spines["right"].set_visible(False)

    fig.tight_layout()
    fig.savefig(OUT / "delta_savings.png")
    plt.close(fig)
    print("  ✓ delta_savings.png")


# ── Chart 5: Accuracy Metrics ──
def chart_accuracy():
    metrics = ["Test\nAccuracy", "INT8/FP32\nAgreement"]
    values = [84.9, 93.75]
    colors = ["#43A047", "#1565C0"]

    fig, ax = plt.subplots(figsize=(4, 3.5))
    bars = ax.bar(metrics, values, color=colors, width=0.4, edgecolor="white", linewidth=1.5)

    for bar, val in zip(bars, values):
        ax.text(bar.get_x() + bar.get_width() / 2, bar.get_height() + 1,
                f"{val}%", ha="center", va="bottom", fontsize=14, fontweight="bold")

    ax.set_ylim(0, 100)
    ax.set_ylabel("Percent (%)")
    ax.spines["top"].set_visible(False)
    ax.spines["right"].set_visible(False)
    ax.axhline(y=80, color="#999", linestyle="--", linewidth=1, alpha=0.5)

    fig.tight_layout()
    fig.savefig(OUT / "accuracy.png")
    plt.close(fig)
    print("  ✓ accuracy.png")


# ── Chart 6: Telegram Bot Flow ──
def chart_bot_flow():
    fig, ax = plt.subplots(figsize=(5.5, 2.5))
    ax.set_xlim(0, 10)
    ax.set_ylim(0, 2)
    ax.axis("off")

    steps = [
        (0.5, "Photo", "#BBDEFB"),
        (2.5, "ML", "#C8E6C9"),
        (4.5, "Env Data", "#FFF9C4"),
        (6.5, "Risk\nEngine", "#FFE0B2"),
        (8.5, "Report", "#E1BEE7"),
    ]

    for i, (x, label, color) in enumerate(steps):
        circle = plt.Circle((x + 0.5, 1), 0.6, color=color, ec="#555", lw=1.5, zorder=2)
        ax.add_patch(circle)
        ax.text(x + 0.5, 1, label, ha="center", va="center", fontsize=8, linespacing=1.3, zorder=3)
        if i < len(steps) - 1:
            next_x = steps[i + 1][0]
            ax.annotate("", xy=(next_x + 0.5 - 0.4, 1), xytext=(x + 0.5 + 0.4, 1),
                        arrowprops=dict(arrowstyle="->", color="#666", lw=1.5))

    fig.tight_layout()
    fig.savefig(OUT / "bot_flow.png")
    plt.close(fig)
    print("  ✓ bot_flow.png")


# ── Chart 7: Deployment Architecture ──
def chart_architecture():
    from matplotlib.patches import FancyBboxPatch

    fig, ax = plt.subplots(figsize=(6.5, 3.5))
    ax.set_xlim(0, 14)
    ax.set_ylim(0, 4)
    ax.axis("off")

    boxes = [
        (0.5, 2.5, 2.5, 1.2, "Telegram\nBot", "#A5D6A7"),
        (3.5, 2.5, 2.5, 1.2, "Web App\n(LiteRT WASM)", "#FFF9C4"),
        (6.5, 2.5, 2.5, 1.2, "Android\n(TFLite)", "#BBDEFB"),
        (9.5, 2.5, 2.5, 1.2, "Supabase\n(DB + Storage)", "#FFE0B2"),
        (0.5, 0.5, 5.5, 1.2, "Open-Meteo / ISRIC\n(Weather, Elevation, Soil)", "#E1BEE7"),
        (6.5, 0.5, 5.5, 1.2, "Model Training\n(Python + DVC)", "#D7CCC8"),
    ]

    for x, y, w, h, label, color in boxes:
        rect = FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0.05",
                               facecolor=color, edgecolor="#555",
                               linewidth=1.5, zorder=2)
        ax.add_patch(rect)
        ax.text(x + w / 2, y + h / 2, label, ha="center", va="center", fontsize=8, linespacing=1.3)

    fig.tight_layout()
    fig.savefig(OUT / "architecture.png")
    plt.close(fig)
    print("  ✓ architecture.png")


# ── Chart 8: Timeline / Stage Progress ──
def chart_timeline():
    stages = ["Data\nPipeline", "Model\nTraining", "Android\nIntegration",
              "HITL\nSystem", "Delta\nOTA", "Telegram\nBot"]
    status = [1, 1, 1, 1, 1, 1]  # all done
    colors = ["#43A047"] * 6

    fig, ax = plt.subplots(figsize=(6.5, 2))
    y_pos = 0

    for i, (stage, done, color) in enumerate(zip(stages, status, colors)):
        x = i * 1.0
        circle = plt.Circle((x + 0.5, 1), 0.35, color=color, ec="#333", lw=1.5, zorder=3)
        ax.add_patch(circle)
        ax.text(x + 0.5, 1, "✓", ha="center", va="center", fontsize=14,
                fontweight="bold", color="white", zorder=4)
        ax.text(x + 0.5, 0.3, stage, ha="center", va="top", fontsize=8)
        if i < len(stages) - 1:
            ax.plot([x + 0.85, x + 1.15], [1, 1], color="#888", linewidth=2, zorder=1)

    ax.set_xlim(-0.2, len(stages) - 0.8)
    ax.set_ylim(0, 2)
    ax.axis("off")

    fig.tight_layout()
    fig.savefig(OUT / "timeline.png")
    plt.close(fig)
    print("  ✓ timeline.png")


if __name__ == "__main__":
    print("Generating charts...")
    chart_model_size()
    chart_inference_perf()
    chart_risk_weights()
    chart_delta_savings()
    chart_accuracy()
    chart_bot_flow()
    chart_architecture()
    chart_timeline()
    print(f"\nDone — {len(list(OUT.glob('*.png')))} charts in {OUT}")
