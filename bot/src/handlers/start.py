import logging

from telegram import Update
from telegram.ext import ContextTypes

logger = logging.getLogger(__name__)


async def start_handler(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    if not update.message:
        return

    await update.message.reply_markdown(
        "👋 *Halo! Saya Bot Retak.id*\n\n"
        "Saya bisa analisis retakan tanah dari foto dan menghasilkan "
        "laporan risiko berdasarkan ML + data lingkungan real-time.\n\n"
        "📸 *Cara pakai:*\n"
        "1. Ketik /lapor untuk memulai\n"
        "2. Kirim foto retakan tanah\n"
        "3. Kirim lokasi retakan\n"
        "4. Review hasil → Konfirmasi simpan\n\n"
        "Hasil: ✅ AMAN / ⚠️ WASPADA / 🔴 BAHAYA\n\n"
        "Powered by MobileNetV2 INT8 | Retak.id",
    )
