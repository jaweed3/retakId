# Bot Retak.id — 🤖 Telegram Bot Analisis Foto+Lingkungan

Bot ini nge-scan foto retakan tanah dari Telegram, jalanin ML model buat klasifikasi risiko (AMAN / WASPADA / BAHAYA), trus digabung sama data lingkungan real-time (curah hujan, kemiringan lereng, elevasi, jenis tanah). Hasilnya laporan risiko multifaktor yang disimpan ke Supabase.

**Dibuat buat operasional temen-temen di Jenangan.** Bot jalan di server sendiri pake Docker, polling mode — gausah domain/SSL.

---

## 🧠 Fitur

| Fitur | Detail |
|-------|--------|
| **Wizard /lapor** | Step-by-step: foto → lokasi → review → simpan ke database |
| **Foto → ML** | Foto retakan tanah diklasifikasi pake MobileNetV2 INT8 |
| **Lokasi wajib** | User harus kirim lokasi — data lingkungan (cuaca, lereng, elevasi, tanah) diambil otomatis dari API |
| **Multi-faktor** | ML (50%) + Lereng (20%) + Curah Hujan (15%) + Elevasi (10%) + Tanah (5%) |
| **Konfirmasi sebelum simpan** | User review laporan dulu → pilih Simpan / Ulangi / Batal |
| **Rate limited** | Maks 10 request per 60 detik per user (bisa diatur) |
| **Notifikasi admin** | Kalo hasil BAHAYA, admin dapet notif langsung |
| **Tes diagnostik** | `/test` buat ngecek ML model + API lingkungan + risk engine |
| **Supabase opsional** | Kalo gaada Supabase, bot tetep jalan (cuma ga nyimpen riwayat) |

---

## 🚀 Cara Jalanin (Buat Temen yang Host)

### 0. Prasyarat

- Server udah ada **Docker** & **Docker Compose** (kalo belom: `apt install docker docker-compose`)
- Bikin bot lewat [@BotFather](https://t.me/BotFather) → dapet **token**
- (Opsional) Project Supabase yang udah siap

### 1. Clone dan Setup

```bash
git clone git@github.com:jaweed3/retakId.git
cd retakId

cp bot/.env.example bot/.env
nano bot/.env
```

### 2. Isi `.env` (Minimal)

Paling penting cuma satu:

```
TELEGRAM_BOT_TOKEN=1234567890:ABCdefGHIjklmNOPqrstUVwxyz
```

Itu doang udah cukup buat bot jalan. Yang lain optional:

| Variable | Kapan Diisi |
|----------|-------------|
| `SUPABASE_URL` + `SUPABASE_SERVICE_KEY` | Kalo mau nyimpen laporan ke database |
| `ADMIN_CHAT_ID` | Kalo mau dapet notifikasi kalo ada temuan BAHAYA |

### 3. Jalanin (Bareng Web + Backend)

```bash
docker compose up -d
```

Ini jalanin **3 service sekaligus**: web (frontend), backend (training), dan bot (Telegram).

Cek log bot:

```bash
docker compose logs -f bot
```

Kalo keliatan `"Bot started (polling)"` — bot udah hidup. Langsung coba kirim `/start` ke bot di Telegram.

> **Catatan:** Backend service running as training container (satu kali jalan, bukan server). Kalo mau jalanin training:
> ```bash
> docker compose --profile train up
> ```

### 4. Setup Command (Sekali Aja)

Buka [@BotFather](https://t.me/BotFather), kirim:

```
/setcommands
```

Pilih bot lu, terus kirim:

```
start - Mulai bot
lapor - Laporkan retakan tanah
health - Cek status bot
stats - Statistik (admin only)
test - Tes diagnostik sistem
batal - Batalkan laporan
```

---

## 🧪 Cara Pake

### Wizard /lapor (Rekomendasi)

Step-by-step:

1. Ketik `/lapor` buat mulai
2. Kirim foto retakan tanah
3. Kirim lokasi (tekan 📎 → Location → Kirim)
4. Bot ambil data lingkungan otomatis
5. Review hasil → tap tombol:
   - **✅ Simpan** → upload foto ke Supabase + simpan laporan
   - **🔄 Ulangi** → ulang dari foto
   - **❌ Batal** → batalkan

### Laporan Cepat (Tanpa Simpan)

Kirim foto aja → bot jawab hasil ML tanpa lingkungan, tanpa simpan.

### Tes Diagnostik

Ketik `/test` buat ngecek semua komponen sekaligus.

Contoh laporan lengkap:

```
🔴 BAHAYA
Skor Risiko: 0.74

📸 Foto: BAHAYA (88% confidence)
🟠 Lereng: curam (12.4°) -> skor 0.40
🟡 Curah Hujan: 4.2 mm -> skor 0.20
🟢 Elevasi: 89 m -> skor 0.10
🟢 Tanah: Acrisol -> skor 0.10

⚠️ Peringatan dini: potensi longsor tinggi.
```

---

## 🏗️ Arsitektur (Buat yang Penasaran)

```
Telegram → bot/src/main.py → ConversationHandler (group=0)
               │
               ├── /lapor → handlers/lapor.py
               │      ├── PHOTO state   → ML inference
               │      ├── LOCATION state → gather 4 API paralel
               │      └── CONFIRM state  → simpan / ulang / batal
               │
               ├── /start  → handlers/start.py
               ├── /stats  → handlers/admin.py (admin only)
               ├── /health → handlers/admin.py
               ├── /test   → handlers/admin.py (diagnostik)
               │
               ├── handlers/error_handler.py — catch all errors
               ├── ml/inference.py           — TFLite INT8 + softmax
               ├── risk/engine.py            — MultiFactorRiskEngine
               ├── services/weather.py       → Open-Meteo API
               ├── services/elevation.py     → Open-Meteo API
               ├── services/slope.py         → Open-Meteo 4-point
               ├── services/soil.py          → ISRIC SoilGrids
               ├── services/supabase.py      → Supabase Storage + DB
               └── middleware/rate_limit.py  — anti spam
```

---

## 🔧 Troubleshooting (Buat Temen)

**Bot ga respon?**
- Cek log: `docker compose logs -f bot`
- Pastiin `TELEGRAM_BOT_TOKEN` bener
- Cek `/health` — harus jawab "Bot sehat"

**Data lingkungan error?**
- Cek log. Biasanya timeout kalo jaringan lemot.
- Slope paling sering gagal (4 request sekaligus). Itu wajar, bot tetep jalan.

**Template foto kepake?**
- Bot nyimpen foto sementara di RAM (ga disimpen permanen). Abis diproses, langsung dibuang.

**Bot lemot?**
- ML inference ≈ 1-2 detik (CPU).
- Data lingkungan ≈ 3-12 detik (tergantung API).
- Total ≈ 5-15 detik per foto + lokasi. Normal.

---

## 🔐 Keamanan

- Bot pake polling → koneksi langsung ke Telegram, ga perlu buka port ke publik
- Kalo pake Supabase, pake **service role key** — jangan pernah commit `.env`
- Rate limiter nyegah spam
- Error handler ga bocorin detail internal ke user
- Admin command (`/stats`) cuma bisa dipake sama user ID yang didaftarin di `ADMIN_IDS`
