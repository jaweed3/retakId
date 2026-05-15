import asyncio
import logging
import time

import numpy as np

from telegram import Update
from telegram.ext import ContextTypes

from handlers.photo import init_predictor
from risk.engine import analyze
from services.elevation import get_elevation
from services.slope import calculate_slope
from services.soil import get_soil_type
from services.weather import get_weather

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


async def test_handler(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    if not update.message:
        return

    msg = await update.message.reply_text("🧪 Menjalankan tes diagnostik...")

    results = []
    all_ok = True

    # 1. Model ML
    try:
        await msg.edit_text("🧪 Tes diagnostik...\n\n📸 Model ML: menguji...")
        predictor = init_predictor(
            context.bot_data.get("model_path"),
            context.bot_data.get("confidence_threshold"),
        )
        dummy = np.random.randint(0, 256, (1, 224, 224, 3), dtype=np.uint8)
        predictor.interpreter.set_tensor(predictor.input_details[0]["index"], dummy)
        predictor.interpreter.invoke()
        output = predictor.interpreter.get_tensor(predictor.output_details[0]["index"])[0]
        assert output is not None and len(output) == 3
        results.append("📸 *Model ML:* ✅ Model loaded & inference OK")
    except Exception as e:
        results.append(f"📸 *Model ML:* ❌ Gagal — {e}")
        all_ok = False

    # 2. API Lingkungan (parallel)
    lat, lon = -7.5, 111.5
    weather = elevation_m = slope_deg = soil = None
    try:
        await msg.edit_text("🧪 Tes diagnostik...\n\n📡 Menguji API lingkungan...")

        weather, elevation_m, slope_deg, soil = await asyncio.gather(
            get_weather(lat, lon),
            get_elevation(lat, lon),
            calculate_slope(lat, lon),
            get_soil_type(lat, lon),
        )

        results.append("✅ *Cuaca*" if weather else "❌ *Cuaca* — Gagal")
        results.append(f"✅ *Elevasi:* {elevation_m:.0f} m" if elevation_m is not None else "❌ *Elevasi* — Gagal")
        results.append(f"✅ *Lereng:* {slope_deg:.0f}°" if slope_deg is not None else "❌ *Lereng* — Gagal")
        results.append(f"✅ *Tanah:* {soil['name']}" if soil else "❌ *Tanah* — Gagal")

        if not all([weather, elevation_m, slope_deg, soil]):
            all_ok = False
    except Exception as e:
        results.append(f"❌ *API Lingkungan* — {e}")
        all_ok = False

    # 3. Risk Engine
    try:
        await msg.edit_text("🧪 Tes diagnostik...\n\n⚙️ Menguji risk engine...")
        rain_mm = weather.get("rain") if weather else None
        report = analyze(
            ml_label="WASPADA",
            ml_confidence=0.85,
            slope_deg=slope_deg,
            rain_mm=rain_mm,
            elevation_m=elevation_m,
            soil_data=soil,
        )
        assert report.final_label in ("AMAN", "WASPADA", "BAHAYA")
        results.append(f"⚙️ *Risk Engine:* ✅ Skor={report.final_score:.2f} → {report.final_label}")
    except Exception as e:
        results.append(f"⚙️ *Risk Engine:* ❌ — {e}")
        all_ok = False

    # 4. Supabase
    supabase_url = context.bot_data.get("supabase_url", "")
    service_key = context.bot_data.get("supabase_service_key", "")
    if supabase_url and service_key:
        results.append("🗄️ *Supabase:* ✅ Terkonfigurasi")
    else:
        results.append("🗄️ *Supabase:* ⚠️ Tidak dikonfigurasi")

    status = "✅ *Semua sistem berfungsi*" if all_ok else "⚠️ *Ada masalah pada beberapa komponen*"
    header = f"🧪 *Hasil Tes Diagnostik*\n{status}\n\n"
    await msg.edit_markdown(header + "\n".join(results))
