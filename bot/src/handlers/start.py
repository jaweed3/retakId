import logging

from telegram import Update
from telegram.ext import ContextTypes

logger = logging.getLogger(__name__)


async def start_handler(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    if not update.message:
        return

    await update.message.reply_markdown(
        "👋 *Halo! Saya Bot Retak.id*\n\n"
        "Saya bisa analisis retakan tanah dari foto dan ngasih laporan risiko "
        "berdasarkan ML + data lingkungan real-time.\n\n"
        "📸 *Cara pakai:*\n\n"
        "**Opsi 1 — Foto aja**\n"
        "Kirim foto retakan → dapet hasil ML (AMAN / WASPADA / BAHAYA).\n\n"
        "**Opsi 2 — Foto + Lokasi (rekomendasi)**\n"
        "Kirim foto + lokasi bersamaan, atau:\n"
        "  • Kirim foto dulu → nanti kirim location\n"
        "  • Kirim location dulu → nanti kirim foto\n"
        "Bot bakal gabungin ML + data lingkungan "
        "(curah hujan, kemiringan lereng, elevasi, jenis tanah) "
        "buat laporan risiko multifaktor.\n\n"
        "📍 Cara kirim lokasi:\n"
        "  HP: 📎 → Location → Kirim\n"
        "  Desktop: 📍 icon → Kirim\n\n"
        "Powered by MobileNetV2 INT8 | Retak.id",
    )
