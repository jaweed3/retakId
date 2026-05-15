import asyncio
import logging
import os
import tempfile
import time

from telegram import Update, ReplyKeyboardMarkup, ReplyKeyboardRemove
from telegram.ext import (
    CommandHandler,
    ConversationHandler,
    ContextTypes,
    MessageHandler,
    filters,
)

from handlers.photo import init_predictor
from risk.engine import analyze
from services.elevation import get_elevation
from services.slope import calculate_slope
from services.soil import get_soil_type
from services.supabase import build_report_data, insert_report, upload_photo
from services.weather import get_weather
from templates.messages import format_risk_report, EMOJI

logger = logging.getLogger(__name__)

PHOTO, LOCATION, CONFIRM = range(3)

SAVE_KEYWORDS = ("simpan", "save", "ya", "y", "yes", "ok", "oke", "oké")
RETRY_KEYWORDS = ("ulangi", "ulang", "retry", "ulang lagi", "coba lagi", "reset")
CANCEL_KEYWORDS = ("batal", "cancel", "tidak", "no", "n", "nggak", "gak", "batalkan")


def _match_keyword(text: str, keywords: tuple[str, ...]) -> bool:
    t = text.strip().lower()
    for kw in keywords:
        if kw in t:
            return True
    return False


async def lapor_start(update: Update, context: ContextTypes.DEFAULT_TYPE) -> int:
    await update.message.reply_markdown(
        "📸 *Lapor Retakan Tanah*\n\n"
        "Kirim foto retakan tanah yang ingin dilaporkan.\n\n"
        "Tips:\n"
        "• Pastikan pencahayaan cukup\n"
        "• Foto dari jarak ~1 meter\n"
        "• Hindari bayangan / silau\n\n"
        "Ketik /batal untuk membatalkan."
    )
    return PHOTO


async def handle_photo(update: Update, context: ContextTypes.DEFAULT_TYPE) -> int:
    if not update.message or not update.message.photo:
        return PHOTO

    photo = update.message.photo[-1]
    file = await photo.get_file()

    tmp_path = ""
    try:
        with tempfile.NamedTemporaryFile(suffix=".jpg", delete=False) as tmp:
            tmp_path = tmp.name

        await file.download_to_drive(tmp_path)

        with open(tmp_path, "rb") as f:
            photo_bytes = f.read()

        predictor = init_predictor(
            context.bot_data.get("model_path"),
            context.bot_data.get("confidence_threshold"),
        )
        pred = predictor.predict(tmp_path)

        context.user_data["photo_bytes"] = photo_bytes
        context.user_data["ml_result"] = {
            "label": pred.label,
            "confidence": pred.confidence,
            "probabilities": pred.probabilities,
        }

        emoji_label = EMOJI.get(pred.label, "❓")

        await update.message.reply_markdown(
            f"{emoji_label} *Hasil ML:* {pred.label} ({pred.confidence:.0%})\n\n"
            "📍 Sekarang *kirim lokasi* retakan tanah ini.\n\n"
            "Caranya: tekan 📎 (attach) → Location → Kirim lokasi saat ini"
        )

    except Exception as e:
        logger.error("Photo processing error: %s", e, exc_info=True)
        await update.message.reply_text(
            "❌ Gagal memproses foto. Coba kirim ulang dengan pencahayaan lebih baik."
        )
        return PHOTO
    finally:
        if tmp_path:
            try:
                os.unlink(tmp_path)
            except OSError:
                pass

    return LOCATION


async def handle_location(update: Update, context: ContextTypes.DEFAULT_TYPE) -> int:
    if not update.message or not update.message.location:
        return LOCATION

    lat = update.message.location.latitude
    lon = update.message.location.longitude
    context.user_data["lat"] = lat
    context.user_data["lon"] = lon

    await update.message.reply_text("🌍 Mengambil data lingkungan...")

    weather = elevation_m = slope_deg = soil = None
    try:
        weather, elevation_m, slope_deg, soil = await asyncio.gather(
            get_weather(lat, lon),
            get_elevation(lat, lon),
            calculate_slope(lat, lon),
            get_soil_type(lat, lon),
        )
    except Exception as e:
        logger.error("Env data fetch failed: %s", e)

    context.user_data["env_data"] = {
        "weather": weather,
        "elevation_m": elevation_m,
        "slope_deg": slope_deg,
        "soil": soil,
    }

    ml = context.user_data["ml_result"]
    rain_mm = weather.get("rain") if weather else None
    report = analyze(
        ml_label=ml["label"],
        ml_confidence=ml["confidence"],
        slope_deg=slope_deg,
        rain_mm=rain_mm,
        elevation_m=elevation_m,
        soil_data=soil,
    )
    context.user_data["report"] = report

    parts = ["📊 Data lingkungan:"]
    parts.append(f"  {'✅' if weather else '❌'} Cuaca")
    parts.append(f"  {'✅' if slope_deg is not None else '❌'} Lereng")
    parts.append(f"  {'✅' if elevation_m is not None else '❌'} Elevasi")
    parts.append(f"  {'✅' if soil else '❌'} Tanah")
    await update.message.reply_text("\n".join(parts))

    text = format_risk_report(report)
    if len(text) > 4096:
        text = text[:4093] + "..."
    await update.message.reply_markdown(text)

    keyboard = [["✅ Simpan", "🔄 Ulangi", "❌ Batal"]]
    reply_markup = ReplyKeyboardMarkup(keyboard, one_time_keyboard=True, resize_keyboard=True)
    await update.message.reply_text(
        "Apakah data di atas sudah benar?",
        reply_markup=reply_markup,
    )

    return CONFIRM


async def handle_confirm(update: Update, context: ContextTypes.DEFAULT_TYPE) -> int:
    text = update.message.text.strip()

    if _match_keyword(text, SAVE_KEYWORDS):
        return await _save_report(update, context)

    elif _match_keyword(text, RETRY_KEYWORDS):
        await update.message.reply_text(
            "📸 Kirim ulang foto retakan tanah.",
            reply_markup=ReplyKeyboardRemove(),
        )
        context.user_data.pop("photo_bytes", None)
        context.user_data.pop("ml_result", None)
        context.user_data.pop("env_data", None)
        context.user_data.pop("report", None)
        context.user_data.pop("lat", None)
        context.user_data.pop("lon", None)
        return PHOTO

    else:
        await update.message.reply_text(
            "🚫 Laporan dibatalkan.", reply_markup=ReplyKeyboardRemove()
        )
        context.user_data.clear()
        return ConversationHandler.END


async def _save_report(update: Update, context: ContextTypes.DEFAULT_TYPE) -> int:
    await update.message.reply_text(
        "Menyimpan laporan...", reply_markup=ReplyKeyboardRemove()
    )

    ml = context.user_data["ml_result"]
    env = context.user_data["env_data"]
    report = context.user_data["report"]
    lat = context.user_data["lat"]
    lon = context.user_data["lon"]
    photo_bytes = context.user_data.get("photo_bytes")
    user = update.effective_user

    name = "?"
    user_id = 0
    if user:
        name = user.username or f"{user.first_name or ''} {user.last_name or ''}".strip()
        user_id = user.id
    pelapor_str = f"@{name}" if name and name != "?" else f"tg:{user_id}"

    context.bot_data["total_analyses"] = context.bot_data.get("total_analyses", 0) + 1

    if report.final_label == "BAHAYA":
        chat_id = context.bot_data.get("admin_chat_id", "")
        if chat_id:
            try:
                await context.bot.send_message(
                    chat_id=int(chat_id),
                    text=(
                        "🚨 *LAPORAN BAHAYA* 🚨\n\n"
                        f"Pelapor: {pelapor_str}\n"
                        f"Skor Risiko: {report.final_score:.2f}\n"
                        f"Koordinat: {lat:.6f}, {lon:.6f}"
                    ),
                    parse_mode="Markdown",
                )
            except Exception as e:
                logger.warning("Admin notification failed: %s", e)

    supabase_url = context.bot_data.get("supabase_url", "")
    service_key = context.bot_data.get("supabase_service_key", "")

    if not supabase_url or not service_key:
        await update.message.reply_markdown(
            f"{EMOJI.get(report.final_label, '✅')} *Analisis selesai!*\n\n"
            "ℹ️ Laporan tidak disimpan ke database (Supabase tidak dikonfigurasi).\n"
            "Hasil ini hanya untuk informasi pribadi."
        )
        logger.debug("Supabase not configured, report not saved")
        context.user_data.clear()
        return ConversationHandler.END

    ts = str(int(time.time()))
    foto_url = None
    if photo_bytes:
        foto_url = await upload_photo(supabase_url, service_key, photo_bytes, user_id, ts)

    catatan = {
        "source": "telegram",
        "telegram_user_id": user_id,
        "ml_label": ml["label"],
        "ml_confidence": round(ml["confidence"], 4),
        "ml_probabilities": [round(p, 4) for p in ml.get("probabilities", [])],
        "final_score": round(report.final_score, 4),
        "factors": [
            {"name": f.name, "score": f.score, "weight": f.weight}
            for f in report.factors
        ],
    }
    s = env["slope_deg"]
    w = env["weather"]
    e = env["elevation_m"]
    sl = env["soil"]
    if s is not None:
        catatan["slope_deg"] = round(s, 1)
    if w and w.get("rain") is not None:
        catatan["rain_mm"] = round(w["rain"], 1)
    if e is not None:
        catatan["elevation_m"] = round(e, 1)
    if sl:
        catatan["soil_code"] = sl.get("code")
        catatan["soil_name"] = sl.get("name")

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
        await update.message.reply_markdown(
            f"{EMOJI.get(report.final_label, '✅')} *Laporan berhasil disimpan!*\n\n"
            "Terima kasih atas partisipasi Anda."
        )
        logger.info("Report saved to Supabase: %s %s", report.final_label, pelapor_str)
    else:
        await update.message.reply_text(
            "❌ Gagal menyimpan laporan ke database. Silakan coba lagi nanti."
        )

    context.user_data.clear()
    return ConversationHandler.END


async def cancel(update: Update, context: ContextTypes.DEFAULT_TYPE) -> int:
    await update.message.reply_text(
        "🚫 Laporan dibatalkan.", reply_markup=ReplyKeyboardRemove()
    )
    context.user_data.clear()
    return ConversationHandler.END


async def _wrong_photo(update: Update, context: ContextTypes.DEFAULT_TYPE) -> int:
    if update.message and update.message.location:
        await update.message.reply_text("Tunggu, kirim foto dulu.")
    else:
        await update.message.reply_text("📸 Kirim foto retakan tanah, bukan teks.")
    return PHOTO


async def _wrong_location(update: Update, context: ContextTypes.DEFAULT_TYPE) -> int:
    if update.message and update.message.photo:
        await update.message.reply_text("Tunggu, kirim lokasi dulu.")
    else:
        await update.message.reply_text("📍 Kirim lokasi retakan tanah, bukan teks.")
    return LOCATION


async def _wrong_confirm(update: Update, context: ContextTypes.DEFAULT_TYPE) -> int:
    await update.message.reply_text(
        "Tap salah satu tombol di bawah:\n"
        "✅ Simpan / 🔄 Ulangi / ❌ Batal\n\n"
        "Atau ketik: Simpan, Ulangi, atau Batal"
    )
    return CONFIRM


def build_conversation_handler() -> ConversationHandler:
    return ConversationHandler(
        entry_points=[CommandHandler("lapor", lapor_start)],
        states={
            PHOTO: [
                MessageHandler(filters.PHOTO, handle_photo),
                MessageHandler(filters.LOCATION, _wrong_photo),
                MessageHandler(filters.TEXT & ~filters.COMMAND, _wrong_photo),
            ],
            LOCATION: [
                MessageHandler(filters.LOCATION, handle_location),
                MessageHandler(filters.PHOTO, _wrong_location),
                MessageHandler(filters.TEXT & ~filters.COMMAND, _wrong_location),
            ],
            CONFIRM: [
                MessageHandler(filters.TEXT & ~filters.COMMAND, handle_confirm),
            ],
        },
        fallbacks=[CommandHandler("batal", cancel)],
        name="lapor_conversation",
        persistent=False,
    )
