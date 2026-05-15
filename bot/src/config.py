import os
from dataclasses import dataclass


@dataclass
class Config:
    telegram_bot_token: str = os.getenv("TELEGRAM_BOT_TOKEN", "")
    model_path: str = os.getenv("MODEL_PATH", "models/retak_mobilenetv2.tflite")
    confidence_threshold: float = float(os.getenv("CONFIDENCE_THRESHOLD", "0.5"))
    mode: str = os.getenv("MODE", "polling")

    supabase_url: str = os.getenv("SUPABASE_URL", "")
    supabase_service_key: str = os.getenv("SUPABASE_SERVICE_KEY", "")

    admin_chat_id: str = os.getenv("ADMIN_CHAT_ID", "")
    admin_ids: list[int] = [
        int(x.strip()) for x in os.getenv("ADMIN_IDS", "").split(",") if x.strip()
    ]

    rate_limit_max: int = int(os.getenv("RATE_LIMIT_MAX", "10"))
    rate_limit_window: int = int(os.getenv("RATE_LIMIT_WINDOW", "60"))

    log_level: str = os.getenv("LOG_LEVEL", "INFO")
