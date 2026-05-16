import asyncio
import logging

from telegram import Update
from telegram.ext import ContextTypes

from risk.engine import analyze
from services.elevation import get_elevation
from services.slope import calculate_slope
from services.soil import get_soil_type
from services.weather import get_weather
from templates.messages import format_risk_report

from handlers.photo import save_analysis_to_supabase, _notify_admin_bahaya

logger = logging.getLogger(__name__)


async def location_handler(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    if not update.message or not update.message.location:
        return

    user = update.effective_user
    limiter = context.bot_data.get("limiter")
    if user and limiter and limiter.is_limited(user.id):
        await update.message.reply_text(
            "⏳ Terlalu banyak permintaan. Harap tunggu beberapa menit.",
        )
        return

    lat = update.message.location.latitude
    lon = update.message.location.longitude
    chat_id = update.message.chat_id

    pending = context.user_data.pop("last_ml", None)

    if not pending:
        await update.message.reply_markdown(
            "📍 Lokasi diterima!\n\n"
            "Sekarang kirim foto retakan tanah di lokasi ini.\n"
            "saya akan analisis ML + faktor lingkungan sekaligus.",
        )
        context.user_data["pending_location"] = {"lat": lat, "lon": lon}
        return

    await update.message.reply_text(
        "🌍 Menggabungkan hasil ML dengan data lingkungan...",
    )

    env_tasks = [
        get_weather(lat, lon),
        get_elevation(lat, lon),
        calculate_slope(lat, lon),
        get_soil_type(lat, lon),
    ]
    weather, elevation_m, slope_deg, soil = await asyncio.gather(*env_tasks)

    parts = ["📊 Data lingkungan:"]
    parts.append(f"  {'✅' if weather else '❌'} Cuaca")
    parts.append(f"  {'✅' if slope_deg is not None else '❌'} Lereng")
    parts.append(f"  {'✅' if elevation_m is not None else '❌'} Elevasi")
    parts.append(f"  {'✅' if soil else '❌'} Tanah")
    await update.message.reply_text("\n".join(parts))

    rain_mm = weather.get("rain") if weather else None

    report = analyze(
        ml_label=pending["label"],
        ml_confidence=pending["confidence"],
        slope_deg=slope_deg,
        rain_mm=rain_mm,
        elevation_m=elevation_m,
        soil_data=soil,
    )

    text = format_risk_report(report)
    if len(text) > 4096:
        text = text[:4093] + "..."
    await update.message.reply_markdown(text)

    user = update.effective_user
    if user:
        name = user.username or f"{user.first_name or ''} {user.last_name or ''}".strip()
        pelapor_str = f"@{name}" if name else f"tg:{user.id}"
        user_id = user.id
    else:
        pelapor_str = "Telegram:?"
        user_id = 0

    context.bot_data["total_analyses"] = context.bot_data.get("total_analyses", 0) + 1

    if report.final_label == "BAHAYA":
        await _notify_admin_bahaya(context, pelapor_str, lat, lon, report)

    await save_analysis_to_supabase(
        context, None, lat, lon,
        pending["label"], pending["confidence"], pending.get("probabilities", []),
        report, weather, slope_deg, elevation_m, soil,
        pelapor_str, user_id,
    )

    logger.info(
        "Sequential analysis chat %s: label=%s score=%.2f",
        chat_id, report.final_label, report.final_score,
    )
