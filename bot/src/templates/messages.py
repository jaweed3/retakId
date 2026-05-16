from ml.inference import PredictionResult
from risk.engine import RiskReport

EMOJI = {"AMAN": "✅", "WASPADA": "⚠️", "BAHAYA": "🔴"}
EMOJI_RISK = {"RENDAH": "🟢", "SEDANG": "🟡", "TINGGI": "🟠", "SANGAT_TINGGI": "🔴"}
FACTOR_ICON = {
    "Analisis Visual": "📸",
    "Kemiringan Lereng": "🏔️",
    "Curah Hujan": "🌧️",
    "Ketinggian": "🏞️",
    "Jenis Tanah": "🧪",
}


def format_prediction(pred: PredictionResult) -> str:
    if pred.label == "TIDAK_PASTI":
        return (
            "🤷 *Hasil: TIDAK PASTI*\n\n"
            f"Keyakinan: {pred.confidence:.0%}\n\n"
            "Foto kurang jelas buat dianalisis. Coba:\n"
            "• Pastikan pencahayaan cukup\n"
            "• Foto dari jarak ~1 meter\n"
            "• Hindari bayangan / silau\n\n"
            "Atau kirim ulang dengan foto yang lebih baik."
        )

    emoji = EMOJI.get(pred.label, "❓")

    return (
        f"{emoji} *{pred.label}*\n\n"
        f"Keyakinan: {pred.confidence:.0%}\n\n"
        f"Probabilitas:\n"
        f"  AMAN    {pred.probabilities[0]:.1%}\n"
        f"  WASPADA {pred.probabilities[1]:.1%}\n"
        f"  BAHAYA  {pred.probabilities[2]:.1%}\n\n"
        "ℹ️ *Catatan:* Hasil ini dari analisis model ML.\n"
        "Kirim juga lokasi untuk analisis risiko lebih lengkap."
    )


def format_risk_report(report: RiskReport) -> str:
    emoji = EMOJI.get(report.final_label, "❓")

    parts = [f"{emoji} *{report.final_label}*"]
    parts.append(f"Skor Risiko: `{report.final_score:.2f}`")

    if report.ml_label == "TIDAK_PASTI":
        parts.append("\n📸 *Analisis Visual*")
        parts.append("  Tidak pasti — faktor lingkungan jadi penentu.")
    else:
        parts.append(f"\n📸 *Analisis Visual:* {report.ml_label} ({report.ml_confidence:.0%})")

    parts.append("\n*Faktor Lingkungan:*")
    for f in report.factors:
        if f.name == "Analisis Visual":
            continue
        icon = FACTOR_ICON.get(f.name, "•")
        parts.append(f"  {icon} {f.name}: {f.raw_value} → {EMOJI_RISK.get(f.label, '')}*{f.label}*")

    if report.is_upgraded:
        parts.append(
            "\n⚠️ Risiko *meningkat* setelah faktor lingkungan."
        )
    elif report.is_downgraded:
        parts.append(
            "\nℹ️ Risiko *menurun* setelah faktor lingkungan."
        )

    parts.append(
        "\n💡 Kirim foto lain atau laporkan ke BPBD setempat jika kondisi memburuk."
    )

    return "\n".join(parts)


def format_location_request() -> str:
    return (
        "📍 *Kirim lokasi untuk analisis lebih lengkap*\n\n"
        "Dengan lokasi, saya bisa cek:\n"
        "• Kemiringan lereng\n"
        "• Curah hujan terkini\n"
        "• Ketinggian tempat\n"
        "• Jenis tanah\n\n"
        "Hasilnya jadi lebih akurat.\n\n"
        "Cara kirim lokasi di Telegram:\n"
        "📎 Attach → Location → Kirim lokasi saat ini"
    )
