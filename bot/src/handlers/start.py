import logging
import time

from telegram import Update
from telegram.ext import ContextTypes

logger = logging.getLogger(__name__)

WEB_URL = "https://retak.utc.web.id"
ANDROID_URL = "https://github.com/jaweed3/retakId/releases/download/v1.1.0/app-debug.apk"
REPEAT_INTERVAL = 3600


async def start_handler(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    if not update.message:
        return

    user = update.effective_user
    name = ""
    if user:
        name = user.first_name or ""

    now = time.time()
    last_start = context.user_data.get("last_start", 0)

    # If user has seen full welcome within the last hour, send short version
    if now - last_start < REPEAT_INTERVAL:
        await update.message.reply_markdown(
            f"👋 Halo {name}! — *Retak.id* masih siap bantu!\n\n"
            "📸 Langsung kirim *foto retakan tanah* untuk langsung diproses\n"
            "📍 Atau kirim *lokasi* untuk analisis lingkungan\n"
            "🤖 /lapor — panduan lengkap\n"
            "🌐 {WEB_URL} — buka website\n\n"
            "Ada yang bisa dibantu?",
        )
        return

    context.user_data["last_start"] = now

    greeting = f"Halo {name}! " if name else "Halo! "
    greeting += "Selamat datang di *Retak.id* — platform crowdsourcing untuk deteksi dini retakan tanah dan pencegahan longsor di Jenangan, Ponorogo."

    await update.message.reply_markdown(
        f"👋 *{greeting}*\n\n"
        "📌 *Apa itu Retak.id?*\n"
        "Kami menggunakan AI (MobileNetV2) untuk menganalisis foto retakan tanah, "
        "dipadukan dengan data lingkungan real-time (curah hujan, kemiringan lereng, "
        "jenis tanah, ketinggian) untuk memberikan skor risiko yang akurat.\n\n"
        "🌐 *Coba Website:*\n"
        f"{WEB_URL} — lihat peta sebaran retakan & lapor lewat web.\n\n"
        "📱 *Download Aplikasi Android:*\n"
        f"{ANDROID_URL} — deteksi offline, laporan online.\n\n"
        "🤖 *Yang bisa saya bantu:*\n"
        "• Kirim *foto* retakan — langsung diproses + deteksi ML\n"
        "• Kirim *lokasi* — analisis lingkungan sekitar\n"
        "• /lapor — panduan lengkap langkah demi langkah\n\n"
        "💡 *Cara cepat:*\n"
        "1️⃣ Kirim foto retakan tanah\n"
        "2️⃣ Kirim lokasi retakan\n"
        "3️⃣ Review hasil → ✅ Simpan / 🔄 Ulangi / ❌ Batal\n\n"
        "Hasil: ✅ AMAN / ⚠️ WASPADA / 🔴 BAHAYA\n\n"
        "Ada pertanyaan? Hubungi tim kami lewat website atau langsung chat di sini!",
    )
