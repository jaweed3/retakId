import logging

from telegram import Update
from telegram.ext import ContextTypes

logger = logging.getLogger(__name__)

WEB_URL = "https://retak.id"
ANDROID_URL = "https://github.com/jaweed3/retakId/releases/download/v1.1.0/app-debug.apk"


async def start_handler(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    if not update.message:
        return

    user = update.effective_user
    name = ""
    if user:
        name = user.first_name or ""

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
        "• /lapor — Laporkan retakan tanah (foto + lokasi → analisis risiko)\n"
        "• Kirim foto langsung — dapatkan prediksi ML cepat\n"
        "• Kirim lokasi — analisis lingkungan tanpa foto\n\n"
        "💡 *Cara pakai /lapor:*\n"
        "1️⃣ Ketik /lapor\n"
        "2️⃣ Kirim foto retakan tanah (pencahayaan cukup, jarak ~1m)\n"
        "3️⃣ Kirim lokasi retakan (tekan 📎 → Location)\n"
        "4️⃣ Review hasil → ✅ Simpan / 🔄 Ulangi / ❌ Batal\n\n"
        "Hasil: ✅ AMAN / ⚠️ WASPADA / 🔴 BAHAYA\n\n"
        "Ada pertanyaan? Hubungi tim kami lewat website atau langsung chat di sini!",
    )
