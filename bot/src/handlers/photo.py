import asyncio
import logging
import os
import tempfile
import time

from telegram import Update
from telegram.ext import ContextTypes

from ml.inference import ModelPredictor
from risk.engine import analyze
from services.elevation import get_elevation
from services.slope import calculate_slope
from services.soil import get_soil_type
from services.supabase import build_report_data, insert_report, upload_photo
from services.weather import get_weather
from templates.messages import format_prediction, format_risk_report

logger = logging.getLogger(__name__)

_predictor: ModelPredictor | None = None


def init_predictor(
    model_path: str | None = None,
    confidence_threshold: float = 0.5,
) -> ModelPredictor:
    global _predictor
    if _predictor is None:
        if model_path is None:
            msg = "model_path required on first call"
            raise RuntimeError(msg)
        _predictor = ModelPredictor(model_path, confidence_threshold)
        logger.info("Model loaded from %s", model_path)
    return _predictor


def _pelapor(update: Update) -> str:
    user = update.effective_user
    if not user:
        return "Telegram:?"
    name = user.username or f"{user.first_name or ''} {user.last_name or ''}".strip()
    return f"@{name}" if name else f"tg:{user.id}"


async def _gather_env_data(
    lat: float,
    lon: float,
) -> tuple:
    weather, elevation_m, slope_deg, soil = await asyncio.gather(
        get_weather(lat, lon),
        get_elevation(lat, lon),
        calculate_slope(lat, lon),
        get_soil_type(lat, lon),
    )
    return weather, elevation_m, slope_deg, soil


async def save_analysis_to_supabase(
    context: ContextTypes.DEFAULT_TYPE,
    photo_bytes: bytes | None,
    lat: float,
    lon: float,
    ml_label: str,
    ml_confidence: float,
    ml_probabilities: list[float],
    report: "RiskReport",
    weather: dict | None,
    slope_deg: float | None,
    elevation_m: float | None,
    soil: dict | None,
    pelapor_str: str,
    user_id: int,
) -> None:
    supabase_url = context.bot_data.get("supabase_url", "")
    service_key = context.bot_data.get("supabase_service_key", "")
    if not supabase_url or not service_key:
        logger.debug("Supabase not configured, skipping save")
        return

    ts = str(int(time.time()))

    foto_url = None
    if photo_bytes:
        foto_url = await upload_photo(supabase_url, service_key, photo_bytes, user_id, ts)

    catatan = {
        "source": "telegram",
        "telegram_user_id": user_id,
        "ml_label": ml_label,
        "ml_confidence": round(ml_confidence, 4),
        "ml_probabilities": [round(p, 4) for p in ml_probabilities],
        "final_score": round(report.final_score, 4),
        "factors": [
            {"name": f.name, "score": f.score, "weight": f.weight}
            for f in report.factors
        ],
    }
    if slope_deg is not None:
        catatan["slope_deg"] = round(slope_deg, 1)
    if weather and weather.get("rain") is not None:
        catatan["rain_mm"] = round(weather["rain"], 1)
    if elevation_m is not None:
        catatan["elevation_m"] = round(elevation_m, 1)
    if soil:
        catatan["soil_code"] = soil.get("code")
        catatan["soil_name"] = soil.get("name")

    data = build_report_data(
        lat=lat,
        lon=lon,
        pelapor=pelapor_str,
        status=report.final_label,
        foto_url=foto_url,
        catatan=catatan,
    )

    ok = await insert_report(supabase_url, service_key, data)
    if ok:
        logger.info("Report saved to Supabase: %s %s", report.final_label, pelapor_str)
    elif supabase_url and service_key:
        logger.error(
            "Supabase configured but insert FAILED for %s (%s)",
            pelapor_str, report.final_label,
        )


async def _notify_admin_bahaya(
    context: ContextTypes.DEFAULT_TYPE,
    pelapor: str,
    lat: float,
    lon: float,
    report: "RiskReport",
) -> None:
    chat_id = context.bot_data.get("admin_chat_id", "")
    if not chat_id:
        return

    text = (
        "🚨 *LAPORAN BAHAYA* 🚨\n\n"
        f"Pelapor: {pelapor}\n"
        f"Skor Risiko: {report.final_score:.2f}\n"
        f"Koordinat: {lat:.6f}, {lon:.6f}\n"
        f"ML: {report.ml_label} ({report.ml_confidence:.0%})\n"
    )

    try:
        await context.bot.send_message(
            chat_id=int(chat_id),
            text=text,
            parse_mode="Markdown",
        )
        logger.info("Admin notified for BAHAYA report from %s", pelapor)
    except Exception as e:
        logger.warning("Admin notification failed: %s", e)


async def _do_full_analysis(
    update: Update,
    context: ContextTypes.DEFAULT_TYPE,
    tmp_path: str,
    photo_bytes: bytes,
    lat: float,
    lon: float,
) -> None:
    predictor = init_predictor()
    pred = predictor.predict(tmp_path)

    await update.message.reply_markdown(
        f"📸 ML: *{pred.label}* ({pred.confidence:.0%})\n"
        "🌍 Mengambil data lingkungan...",
    )

    weather, elevation_m, slope_deg, soil = await _gather_env_data(lat, lon)

    parts = ["📊 Data lingkungan:"]
    parts.append(f"  {'✅' if weather else '❌'} Cuaca")
    parts.append(f"  {'✅' if slope_deg is not None else '❌'} Lereng")
    parts.append(f"  {'✅' if elevation_m is not None else '❌'} Elevasi")
    parts.append(f"  {'✅' if soil else '❌'} Tanah")
    await update.message.reply_text("\n".join(parts))

    rain_mm = weather.get("rain") if weather else None
    report = analyze(
        ml_label=pred.label,
        ml_confidence=pred.confidence,
        slope_deg=slope_deg,
        rain_mm=rain_mm,
        elevation_m=elevation_m,
        soil_data=soil,
    )

    text = format_risk_report(report)
    if len(text) > 4096:
        text = text[:4093] + "..."
    await update.message.reply_markdown(text)

    pelapor_str = _pelapor(update)
    user_id = update.effective_user.id if update.effective_user else 0

    context.bot_data["total_analyses"] = context.bot_data.get("total_analyses", 0) + 1

    if report.final_label == "BAHAYA":
        await _notify_admin_bahaya(context, pelapor_str, lat, lon, report)

    await save_analysis_to_supabase(
        context, photo_bytes, lat, lon,
        pred.label, pred.confidence, pred.probabilities,
        report, weather, slope_deg, elevation_m, soil,
        pelapor_str, user_id,
    )

    logger.info(
        "Full analysis: %s score=%.2f lat=%.4f lon=%.4f",
        report.final_label, report.final_score, lat, lon,
    )


async def photo_handler(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    if not update.message or not update.message.photo:
        return

    user = update.effective_user
    limiter = context.bot_data.get("limiter")
    if user and limiter and limiter.is_limited(user.id):
        await update.message.reply_text(
            "⏳ Terlalu banyak permintaan. Harap tunggu beberapa menit.",
        )
        return

    chat_id = update.message.chat_id
    photo = update.message.photo[-1]
    file = await photo.get_file()

    logger.info("Processing photo from chat %s (file_id=%s)", chat_id, file.file_id)

    await update.message.reply_text("🔍 Menganalisis foto, tunggu sebentar...")

    tmp_path = ""
    try:
        with tempfile.NamedTemporaryFile(suffix=".jpg", delete=False) as tmp:
            tmp_path = tmp.name

        await file.download_to_drive(tmp_path)

        with open(tmp_path, "rb") as f:
            photo_bytes = f.read()

        location = update.message.location
        pending_loc = context.user_data.pop("pending_location", None)

        if location or pending_loc:
            lat = location.latitude if location else pending_loc["lat"]
            lon = location.longitude if location else pending_loc["lon"]
            await _do_full_analysis(update, context, tmp_path, photo_bytes, lat, lon)
        else:
            predictor = init_predictor()
            pred = predictor.predict(tmp_path)

            text = format_prediction(pred)
            await update.message.reply_markdown(text)

            context.user_data["last_ml"] = {
                "label": pred.label,
                "confidence": pred.confidence,
                "probabilities": pred.probabilities,
            }

            context.bot_data["total_analyses"] = context.bot_data.get("total_analyses", 0) + 1

            logger.info(
                "Prediction for chat %s: label=%s confidence=%.2f",
                chat_id,
                pred.label,
                pred.confidence,
            )

    except Exception as e:
        logger.error("Prediction error for chat %s: %s", chat_id, e, exc_info=True)
        await update.message.reply_text(
            "❌ Maaf, gagal memproses foto. "
            "Coba kirim ulang dengan pencahayaan lebih baik.",
        )
    finally:
        if tmp_path:
            try:
                os.unlink(tmp_path)
            except OSError:
                pass
