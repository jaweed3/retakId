# Deploy Bot Retak.id

## Prerequisites

- Docker & Docker Compose di server
- Telegram Bot Token (dari [@BotFather](https://t.me/BotFather))
- (Opsional) Supabase project untuk database

## Quick Start

```bash
# 1. Clone repo
git clone <repo-url> retakid
cd retakid

# 2. Setup env
cp bot/.env.example bot/.env
nano bot/.env   # isi TELEGRAM_BOT_TOKEN

# 3. Build & run semua service
docker compose up -d

# 4. Cek log bot
docker compose logs -f bot
```

## Environment Variables

| Variable | Wajib | Default | Deskripsi |
|----------|-------|---------|-----------|
| `TELEGRAM_BOT_TOKEN` | ✅ | — | Token dari BotFather |
| `SUPABASE_URL` | ❌ | — | URL Supabase project |
| `SUPABASE_SERVICE_KEY` | ❌ | — | Service role key (bukan anon) |
| `ADMIN_CHAT_ID` | ❌ | — | Chat ID untuk notifikasi BAHAYA |
| `ADMIN_IDS` | ❌ | — | User ID admin (dipisah koma) |
| `CONFIDENCE_THRESHOLD` | ❌ | `0.5` | Threshold ML (0.0–1.0) |
| `RATE_LIMIT_MAX` | ❌ | `10` | Max request per window |
| `RATE_LIMIT_WINDOW` | ❌ | `60` | Window dalam detik |
| `LOG_LEVEL` | ❌ | `INFO` | DEBUG / INFO / WARNING |

## Bot Commands

| Command | Akses | Fungsi |
|---------|-------|--------|
| `/start` | Semua | Welcome + cara pakai |
| `/health` | Semua | Cek status bot |
| `/stats` | Admin | Statistik bot |

## Struktur

```
bot/
├── src/
│   ├── main.py               # Entry point
│   ├── config.py              # Env config
│   ├── handlers/              # Telegram handlers
│   │   ├── start.py           # /start
│   │   ├── photo.py           # Foto → ML → risk → supabase
│   │   ├── location.py        # Lokasi + pending ML
│   │   └── admin.py           # /stats, /health
│   ├── ml/                    # ML inference
│   │   ├── preprocess.py      # Resize 224×224, RGB uint8
│   │   └── inference.py       # TFLite predict + softmax
│   ├── risk/                  # MultiFactorRiskEngine
│   │   └── engine.py          # 5 faktor: ML, slope, rain, elev, soil
│   ├── services/              # External APIs
│   │   ├── weather.py         # Open-Meteo
│   │   ├── elevation.py       # Open-Meteo
│   │   ├── slope.py           # 4-point calculation
│   │   ├── soil.py            # ISRIC SoilGrids
│   │   └── supabase.py        # Upload + insert
│   ├── middleware/             # Request pipeline
│   │   └── rate_limit.py      # Rate limiter
│   └── templates/             # Message templates
│       └── messages.py
├── Dockerfile
├── README.md
├── docs/
│   └── deploy.md
├── pyproject.toml
└── .env.example
```

## Root Compose

Service bot udah terintegrasi di `docker-compose.yml` root, bareng `web` (frontend) dan `backend` (training):

```bash
# Start semua service
docker compose up -d

# Start cuma bot doang
docker compose up -d bot

# Training backend (profile)
docker compose --profile train up backend
```

## Model

Model `retak_mobilenetv2.tflite` diambil dari `backend/models/` saat build. Int8 quantized, input uint8 [0,255] 224×224, output 3 float32 logits.

## Catatan

- Bot pake **polling** mode, ga perlu domain/SSL
- Untuk **webhook** (production): set `MODE=webhook`, `WEBHOOK_URL`, `WEBHOOK_SECRET`
- Semua external API gratis (Open-Meteo, ISRIC) — no API key required
- Rate limiting: max 10 request per 60 detik per user (configurable)
