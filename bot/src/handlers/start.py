import logging

from telegram import Update
from telegram.ext import ContextTypes

logger = logging.getLogger(__name__)


async def start_handler(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    if not update.message:
        return

    await update.message.reply_markdown(
        "👋 *Halo! Saya Bot Retak.id*\n\n"
        "Saya bisa analisis retakan tanah dari foto.\n\n"
        "📸 *Cara pakai:*\n"
        "1. Kirim foto retakan (usahakan cahaya cukup)\n"
        "2. Saya analisis pake model ML\n"
        "3. Dapat hasil: ✅ AMAN / ⚠️ WASPADA / 🔴 BAHAYA\n\n"
        "📍 (Opsional) Kirim lokasi untuk analisis risiko yang lebih akurat.\n\n"
        "Powered by MobileNetV2 INT8 | Retak.id",
    )
