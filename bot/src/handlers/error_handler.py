import logging
import traceback

from telegram import Update
from telegram.ext import ContextTypes

logger = logging.getLogger(__name__)


async def error_handler(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    err = context.error
    tb = ""
    if err and err.__traceback__:
        tb = "".join(traceback.format_tb(err.__traceback__))

    logger.error(
        "Unhandled error: %s\nUpdate: %s\nTraceback:\n%s",
        err, update, tb,
    )

    if err:
        try:
            raise err
        except Exception:
            logger.exception("Full traceback:")

    if update and update.effective_message:
        try:
            await update.effective_message.reply_text(
                "❌ Terjadi kesalahan internal. Silakan coba lagi.",
            )
        except Exception:
            pass
