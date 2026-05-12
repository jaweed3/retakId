# Retak.id — Platform Crowdsourcing Deteksi Dini Retakan Tanah

MVP untuk **IYREF 2026 Semi-Final** — Kategori _Climate Resilience & Local Wisdom_. Retak.id adalah platform crowdsourcing berbasis Android dan Web untuk deteksi dini retakan tanah longsor di **Jenangan, Ponorogo**.

## Ringkasan

Retak.id memungkinkan warga dan petugas BPBD untuk mendeteksi, melaporkan, dan memantau retakan tanah secara _offline-first_. Aplikasi Android menggunakan CameraX + TensorFlow Lite (INT8) untuk klasifikasi retakan langsung di perangkat **tanpa koneksi internet**. Laporan dikirim ke backend Supabase dan ditampilkan di **web dashboard** untuk agregasi dan pemantauan publik.

## Arsitektur Sistem

```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│   ANDROID APP   │────▶│    SUPABASE      │◀────│   WEB DASHBOARD  │
│  (Kotlin/TFLite)│     │  (PostgreSQL)    │     │  (React/Vite)    │
│                 │     │  + Auth          │     │                  │
│  CameraX        │     │  + Storage       │     │  Peta interaktif │
│  TFLite INT8    │     │  + Realtime      │     │  List laporan    │
│  Offline-first  │     │                  │     │  Filter + Search │
└─────────────────┘     └──────────────────┘     └─────────────────┘
```

## Struktur Proyek

| Direktori | Deskripsi | Teknologi |
|-----------|-----------|-----------|
| `web-app/` | **Web Dashboard** — Peta, list laporan, filter, detail | React, Vite, TypeScript, Leaflet, Tailwind CSS |
| `backend/` | **ML Pipeline** — Scraping, training, quantization, export | Python, TensorFlow, DVC, MLflow |
| `docs/` | **Dokumentasi teknis** — Arsitektur, model detail, kontrak inference | Markdown |
| `mobile-app/` | **Android App** — Kamera, inferensi TFLite, laporan | Kotlin, Jetpack Compose, CameraX |

> **Catatan**: Android app berada di branch `mobile-app` karena dikembangkan paralel dengan ML pipeline.

## Tech Stack Lengkap

| Layer | Teknologi |
|-------|-----------|
| **Mobile** | Kotlin, Jetpack Compose, CameraX, TensorFlow Lite (INT8) |
| **Web** | React 18, Vite 6, TypeScript, Tailwind CSS 3, Leaflet, React Router 6 |
| **ML** | Python 3.11, TensorFlow 2.15+, MobileNetV2 (transfer learning), INT8 PTQ |
| **Backend (BaaS)** | Supabase — PostgreSQL, Auth, Storage, Realtime |
| **Data Pipeline** | DuckDuckGo Image Scraping, perceptual hashing, OpenCV |
| **Experiment Tracking** | MLflow, DagsHub |
| **Data Versioning** | DVC (remote: DagsHub S3-compatible) |
| **Package Manager** | `uv` (Python), `npm` (Node.js) |
| **Testing** | pytest (16 tests) |
| **Deploy** | Vercel (web), Docker (training) |

## Quick Links

| Dokumen | Isi |
|---------|-----|
| [DOKUMENTASI.md](DOKUMENTASI.md) | Dokumentasi lengkap seluruh proyek |
| [docs/architecture.md](docs/architecture.md) | Arsitektur teknis Edge-First |
| [docs/getting_started.md](docs/getting_started.md) | Panduan setup dari nol |
| [docs/model_detail.md](docs/model_detail.md) | Detail model ML + hasil eksperimen |
| [docs/inference_contract.md](docs/inference_contract.md) | Kontrak input/output TFLite |

## Cara Menjalankan

### Web Dashboard

```bash
cd web-app
cp .env.example .env.local     # Isi VITE_SUPABASE_URL dan VITE_SUPABASE_ANON_KEY
npm install
npm run dev                     # Development server di http://localhost:5173
```

### ML Pipeline

```bash
cd backend
uv sync                         # Install dependencies
make scrape                     # Scraping gambar retakan
make train                      # Training + evaluasi + export TFLite
make test                       # Jalankan pytest
```

## Tim

| Nama | Peran |
|------|-------|
| **Farrel Ghozy** | Data Acquisition & Annotation, Web Dashboard |
| **Adam Nurwahid** | Android Development |
| **Jaweed (Fatih)** | ML Pipeline & Infrastructure |

## Lisensi

Proyek lomba IYREF 2026. Hak cipta pada tim Retak.id.
