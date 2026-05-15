import logging
import time

from telegram import Update
from telegram.ext import ContextTypes

logger = logging.getLogger(__name__)


async def stats_handler(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    user = update.effective_user
    if not user or not update.message:
        return

    admin_ids: list[int] = context.bot_data.get("admin_ids", [])
    if user.id not in admin_ids:
        await update.message.reply_text("❌ Perintah hanya untuk admin.")
        return

    total = context.bot_data.get("total_analyses", 0)
    start = context.bot_data.get("start_time", time.time())
    uptime_sec = time.time() - start

    hours, remainder = divmod(int(uptime_sec), 3600)
    minutes, seconds = divmod(remainder, 60)

    lines = [
        "📊 *Statistik Bot*",
        f"Total analisis: {total}",
        f"Uptime: {hours}j {minutes}m {seconds}d",
    ]

    cfg = context.bot_data
    if cfg.get("supabase_url"):
        lines.append("Database: ✅ Terhubung")
    else:
        lines.append("Database: ❌ Tidak dikonfigurasi")

    await update.message.reply_markdown("\n".join(lines))


async def health_handler(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    if not update.message:
        return
    await update.message.reply_markdown("✅ *Bot sehat*\nModel: MobileNetV2 INT8")
