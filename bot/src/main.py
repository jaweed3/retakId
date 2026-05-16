import logging
import signal
import time

from telegram.ext import (
    Application,
    CommandHandler,
    MessageHandler,
    filters,
)

from config import Config
from handlers.admin import health_handler, stats_handler, test_handler
from handlers.error_handler import error_handler
from handlers.lapor import build_conversation_handler
from handlers.location import location_handler
from handlers.photo import init_predictor, photo_handler
from handlers.start import start_handler
from middleware.rate_limit import RateLimiter

logging.basicConfig(
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    level=logging.INFO,
)
logger = logging.getLogger(__name__)


def setup_logging(level: str) -> None:
    logging.getLogger().setLevel(level.upper())


def main() -> None:
    cfg = Config()
    setup_logging(cfg.log_level)

    if not cfg.telegram_bot_token:
        logger.error("TELEGRAM_BOT_TOKEN is not set")
        return

    limiter = RateLimiter(
        max_requests=cfg.rate_limit_max,
        window=cfg.rate_limit_window,
    )

    app = Application.builder().token(cfg.telegram_bot_token).build()

    app.bot_data["model_path"] = cfg.model_path
    app.bot_data["confidence_threshold"] = cfg.confidence_threshold
    app.bot_data["supabase_url"] = cfg.supabase_url
    app.bot_data["supabase_service_key"] = cfg.supabase_service_key
    app.bot_data["admin_chat_id"] = cfg.admin_chat_id
    app.bot_data["admin_ids"] = cfg.admin_ids
    app.bot_data["limiter"] = limiter
    app.bot_data["total_analyses"] = 0
    app.bot_data["start_time"] = time.time()

    init_predictor(cfg.model_path, cfg.confidence_threshold)

    app.add_error_handler(error_handler)

    app.add_handler(build_conversation_handler(), group=0)
    app.add_handler(CommandHandler("start", start_handler), group=0)
    app.add_handler(MessageHandler(filters.PHOTO & ~filters.COMMAND, photo_handler), group=0)
    app.add_handler(MessageHandler(filters.LOCATION & ~filters.COMMAND, location_handler), group=0)
    app.add_handler(CommandHandler("stats", stats_handler), group=1)
    app.add_handler(CommandHandler("health", health_handler), group=1)
    app.add_handler(CommandHandler("test", test_handler), group=2)

    logger.info(
        "Bot started — mode=%s rate=%d/%ds admin_chat=%s",
        cfg.mode, cfg.rate_limit_max, cfg.rate_limit_window,
        cfg.admin_chat_id or "none",
    )

    app.run_polling(
        allowed_updates=["message"],
        stop_signals=(signal.SIGINT, signal.SIGTERM),
    )

    logger.info("Bot stopped")


if __name__ == "__main__":
    main()
