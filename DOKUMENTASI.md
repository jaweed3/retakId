# DOKUMENTASI.md — Retak.id

Dokumentasi lengkap platform **Retak.id**: crowdsourcing deteksi dini retakan tanah longsor berbasis Android dan Web untuk Jenangan, Ponorogo. Proyek ini dibangun untuk kompetisi **IYREF 2026 Semi-Final** kategori _Climate Resilience & Local Wisdom_.

---

## Daftar Isi

1. [Visi & Latar Belakang](#1-visi--latar-belakang)
2. [Arsitektur Sistem](#2-arsitektur-sistem)
3. [Komponen 1: Web Dashboard](#3-komponen-1-web-dashboard)
4. [Komponen 2: Android App](#4-komponen-2-android-app)
5. [Komponen 3: ML Pipeline](#5-komponen-3-ml-pipeline)
6. [Komponen 4: Backend (Supabase)](#6-komponen-4-backend-supabase)
7. [Data Pipeline & Versioning](#7-data-pipeline--versioning)
8. [Model Machine Learning](#8-model-machine-learning)
9. [Panduan Setup & Menjalankan](#9-panduan-setup--menjalankan)
10. [Deployment](#10-deployment)
11. [Testing](#11-testing)
12. [Tim & Peran](#12-tim--peran)
13. [FAQ](#13-faq)

---

## 1. Visi & Latar Belakang

### Masalah

Jenangan, Ponorogo merupakan daerah rawan longsor dengan infrastruktur internet terbatas. Tanah retak sering menjadi tanda awal longsor, tetapi warga tidak memiliki alat untuk mengidentifikasi tingkat bahayanya. BPBD (Badan Penanggulangan Bencana Daerah) juga kesulitan memantau kondisi tanah secara real-time karena tidak ada sistem pelaporan terpusat.

### Solusi

**Retak.id** menyediakan:

1. **Aplikasi Android offline-first** — warga memfoto retakan tanah, dan AI di dalam HP langsung mengklasifikasikan tingkat bahaya (AMAN / WASPADA / BAHAYA) **tanpa koneksi internet**
2. **Web Dashboard** — laporan dari seluruh warga ditampilkan di peta interaktif, bisa diakses BPBD untuk pemantauan terpusat
3. **Backend Supabase** — jembatan antara Android dan Web, menyediakan database, storage foto, dan notifikasi realtime

### Tiga Tingkat Klasifikasi

| Status | Arti | Tindakan |
|--------|------|----------|
| **AMAN** | Retakan minor akibat penyusutan alami | Tidak perlu tindakan khusus |
| **WASPADA** | Retakan signifikan, perlu perhatian | Laporkan ke ketua RT/RW, pantau berkala |
| **BAHAYA** | Retakan kritis, indikasi pergerakan tanah besar | Evakuasi, hubungi BPBD segera |

---

## 2. Arsitektur Sistem

```
                            ┌─────────────────────────┐
                            │       SUPABASE           │
                            │  ┌───────────────────┐   │
                            │  │ PostgreSQL         │   │
                            │  │ - users           │   │
                            │  │ - laporan         │   │
                            │  ├───────────────────┤   │
                            │  │ Auth (JWT)        │   │
                            │  │ Storage (S3)      │   │
                            │  │ Realtime (WS)     │   │
                            │  └───────────────────┘   │
                            └──────┬──────────┬────────┘
                                   │          │
                      ┌────────────┘          └────────────┐
                      ▼                                    ▼
┌──────────────────────────────┐     ┌──────────────────────────────┐
│         ANDROID APP          │     │        WEB DASHBOARD          │
│  ┌────────────────────────┐  │     │  ┌────────────────────────┐  │
│  │ CameraX → Bitmap       │  │     │  │ React 18 + Vite 6      │  │
│  │   ↓                    │  │     │  │ TypeScript             │  │
│  │ Preprocessing          │  │     │  │ Tailwind CSS           │  │
│  │ 224×224 RGB uint8      │  │     │  └────────────────────────┘  │
│  │   ↓                    │  │     │  ┌────────────────────────┐  │
│  │ TFLite INT8 Inference  │  │     │  │ Halaman:               │  │
│  │   ↓                    │  │     │  │ /             Peta     │  │
│  │ AMAN/WASPADA/BAHAYA    │  │     │  │ /reports      List    │  │
│  └────────────────────────┘  │     │  │ /reports/:id  Detail  │  │
│  ┌────────────────────────┐  │     │  └────────────────────────┘  │
│  │ Laporan → Supabase     │  │     │  ┌────────────────────────┐  │
│  │ - Foto ke Storage      │  │     │  │ Leaflet Map            │  │
│  │ - Data ke PostgreSQL   │  │     │  │ StatusBadge            │  │
│  │ - Auth user            │  │     │  │ FilterStatusBar        │  │
│  └────────────────────────┘  │     │  │ StatsSummaryCards      │  │
│                              │     │  │ LaporanCard            │  │
│  Kotlin + Jetpack Compose   │     │  │ Dark/Light Mode        │  │
│  CameraX + TFLite INT8      │     │  │ Responsive             │  │
│  Supabase Kotlin SDK        │     │  │ Supabase JS SDK        │  │
└──────────────────────────────┘     └──────────────────────────────┘

┌────────────────────────────────────────────────────────────────┐
│                       ML PIPELINE (offline)                     │
│  Scrape → Validate → Deduplicate → Split (70/15/15)           │
│     → Augment → MobileNetV2 Training → INT8 PTQ → .tflite     │
│                                                                │
│  Python 3.11 + TensorFlow 2.15 + DVC + MLflow + Docker         │
└────────────────────────────────────────────────────────────────┘
```

### Prinsip Desain

| Prinsip | Implementasi |
|---------|-------------|
| **Edge-First** | Inferensi ML di HP, tidak perlu internet |
| **BaaS (Backend as a Service)** | Supabase gantikan server custom — PostgreSQL, Auth, Storage, Realtime dalam satu platform |
| **Static Web** | Dashboard React di-build jadi HTML/CSS/JS statis, di-deploy ke Vercel — tidak perlu server side rendering |
| **Data Versioning** | Dataset dan model di-versioning dengan DVC + DagsHub |
| **Reproducible** | Dependency lock (`uv.lock`, `package-lock.json`), seed tetap, Docker environment |

---

## 3. Komponen 1: Web Dashboard

**Lokasi**: `web-app/`  
**Teknologi**: React 18, Vite 6, TypeScript, Tailwind CSS 3, Leaflet, Supabase JS SDK, React Router 6  
**Deploy**: Vercel (static hosting)

### 3.1 Struktur File

```
web-app/
├── index.html                        # Entry HTML
├── package.json                      # Dependensi & scripts
├── vite.config.ts                    # Konfigurasi Vite + path alias
├── tailwind.config.js                # Tema kustom (warna Retak.id)
├── tsconfig.json                     # Konfigurasi TypeScript
├── .env.example                      # Template kredensial Supabase
├── .gitignore
├── public/
│   └── retak-favicon.svg             # Ikon aplikasi
└── src/
    ├── main.tsx                      # Entry React (BrowserRouter + ThemeProvider)
    ├── App.tsx                       # Router definition
    ├── index.css                     # Tailwind directives + CSS variables (light/dark)
    ├── types/
    │   └── laporan.ts                # Interface Laporan, ReportStatus, Database
    ├── lib/
    │   └── supabase.ts               # Supabase client + requireSupabase()
    ├── context/
    │   └── ThemeContext.tsx           # Dark/light mode context + toggle + localStorage
    ├── hooks/
    │   └── useLaporan.ts             # Fetch, filter, realtime subscription
    ├── utils/
    │   ├── cn.ts                     # clsx + tailwind-merge helper
    │   ├── statusColors.ts           # Mapping status → warna
    │   └── formatDate.ts             # Format tanggal relatif (bahasa Indonesia)
    ├── components/
    │   ├── Layout.tsx                # Sidebar (desktop) + bottom nav (mobile)
    │   ├── ThemeToggle.tsx           # Tombol switch dark/light
    │   ├── StatusBadge.tsx           # Badge AMAN (hijau) / WASPADA (oranye) / BAHAYA (merah)
    │   ├── LoadingSpinner.tsx        # Spinner animasi + teks
    │   ├── ErrorState.tsx            # Pesan error + tombol "Coba Lagi"
    │   ├── EmptyState.tsx            # Ilustrasi + teks saat data kosong
    │   ├── StatsSummaryCards.tsx     # 3 kartu statistik (loading skeleton + data)
    │   ├── FilterStatusBar.tsx       # Chip filter: Semua / AMAN / WASPADA / BAHAYA + count
    │   ├── MapView.tsx               # Peta Leaflet + custom marker + state handling
    │   ├── LaporanCard.tsx           # Kartu laporan (garis status + info)
    │   └── LaporanMapPopup.tsx       # Isi popup saat klik marker di peta
    └── pages/
        ├── DashboardPage.tsx         # Halaman utama: peta + stats overlay + filter
        ├── ReportsPage.tsx           # List laporan + search + filter + pagination
        └── ReportDetailPage.tsx      # Detail laporan + foto + info + mini map
```

### 3.2 Route & Halaman

| Route | Halaman | Komponen Utama |
|-------|---------|---------------|
| `/` | DashboardPage | MapView, StatsSummaryCards, FilterStatusBar |
| `/reports` | ReportsPage | Search bar, FilterStatusBar, LaporanCard grid, Pagination |
| `/reports/:id` | ReportDetailPage | Foto, StatusBadge, Info grid, Catatan, Mini map |

### 3.3 State Handling

Setiap komponen yang menampilkan data menangani **5 state**:

| State | Tampilan |
|-------|----------|
| **Loading** | Skeleton/spinner — `LoadingSpinner` atau `SkeletonCards` |
| **Error** | Pesan error + tombol "Coba Lagi" — `ErrorState` |
| **Empty** | Ilustrasi + teks "Belum ada laporan" — `EmptyState` |
| **Filtered Empty** | "Tidak ada laporan dengan filter ini" |
| **Success** | Data normal |

### 3.4 Tema (Light & Dark Mode)

Tema mengikuti Android app. Definisi di `index.css` sebagai CSS custom properties, di-switch dengan class `.dark` di `<html>`.

#### Light Mode

| Token | Hex | Penggunaan |
|-------|-----|-----------|
| Primary | `#2E7D32` | Tombol, link, ikon aktif, sidebar aktif |
| Primary Light | `#4CAF50` | Hover state, aksen |
| Primary Surface | `#E8F5E9` | Background item aktif |
| AMAN | `#388E3C` | Badge + marker hijau |
| AMAN Bg | `#E8F5E9` | Background badge hijau |
| WASPADA | `#F57C00` | Badge + marker oranye |
| WASPADA Bg | `#FFF3E0` | Background badge oranye |
| BAHAYA | `#D32F2F` | Badge + marker merah |
| BAHAYA Bg | `#FFEBEE` | Background badge merah |
| Text Primary | `#1B1B1B` | Judul, body text |
| Text Secondary | `#6D6D6D` | Label, deskripsi |
| Surface | `#F5F5F5` | Background halaman |
| Card | `#FFFFFF` | Background kartu |
| Divider | `#E0E0E0` | Garis pemisah |

#### Dark Mode

| Token | Hex | Penggunaan |
|-------|-----|-----------|
| Primary | `#4CAF50` | Lebih terang untuk kontras |
| Primary Surface | `#1B3A1B` | Hijau gelap |
| AMAN | `#4CAF50` | Lebih terang |
| AMAN Bg | `#1B3A1B` | Hijau sangat gelap |
| WASPADA | `#FF9800` | Sedikit lebih terang |
| WASPADA Bg | `#3A2E1B` | Oranye sangat gelap |
| BAHAYA | `#EF5350` | Lebih terang |
| BAHAYA Bg | `#3A1B1B` | Merah sangat gelap |
| Text Primary | `#E8E8E8` | Putih pudar |
| Text Secondary | `#A0A0A0` | Abu-abu terang |
| Surface | `#121212` | Hitam material |
| Card | `#1E1E1E` | Abu-abu sangat gelap |
| Divider | `#333333` | Abu-abu gelap |

### 3.5 Desain Responsif

| Viewport | Layout |
|----------|--------|
| **Desktop** (≥1024px) | Sidebar kiri (240px) + konten utama scrollable |
| **Tablet** (768-1023px) | Sama dengan desktop, sidebar lebih kecil |
| **Mobile** (<768px) | Header top + konten scrollable + bottom navigation bar |

---

## 4. Komponen 2: Android App

**Lokasi**: Branch `mobile-app`  
**Teknologi**: Kotlin, Jetpack Compose, CameraX, TensorFlow Lite (INT8), Supabase Kotlin SDK

### 4.1 Fitur

| Fitur | Deskripsi |
|-------|-----------|
| **Deteksi Retakan** | Buka kamera → ambil foto → klasifikasi AMAN/WASPADA/BAHAYA secara offline |
| **Laporan** | Kirim hasil deteksi + foto + lokasi GPS ke Supabase |
| **Beranda** | Lihat daftar laporan dari seluruh pengguna |
| **Peta** | Lihat sebaran laporan di peta |
| **Profil** | Manajemen akun pengguna |
| **Autentikasi** | Register + Login + Session management via Supabase Auth |

### 4.2 Struktur Kunci

```
mobile-app/app/src/main/java/com/unidagontor/retakid/
├── RetakIdApplication.kt          # Inisialisasi Supabase client
├── data/
│   └── SupabaseClient.kt          # Singleton Supabase (Auth, Postgrest, Storage, Realtime)
├── ui/
│   ├── theme/
│   │   ├── Color.kt               # Definisi warna
│   │   ├── Theme.kt               # Material3 light color scheme
│   │   └── Type.kt                # Typography
│   ├── screens/
│   │   ├── AppNavigation.kt       # Navigasi utama
│   │   ├── BerandaScreen.kt       # Feed laporan
│   │   ├── LoginScreen.kt         # Login
│   │   ├── RegisterScreen.kt      # Registrasi
│   │   ├── ProfilScreen.kt        # Profil pengguna
│   │   └── SplashScreen.kt        # Splash screen
│   └── viewmodel/
│       ├── BerandaViewModel.kt    # ViewModel beranda
│       ├── DeteksiViewModel.kt    # ViewModel deteksi
│       └── ProfilViewModel.kt     # ViewModel profil
└── util/
    └── BitmapUtils.kt             # Preprocessing bitmap untuk TFLite
```

### 4.3 Alur Deteksi

```
1. User buka kamera via CameraX
2. User tekan tombol capture
3. Bitmap → resize 224×224 → RGB ByteBuffer [0, 255]
4. TFLite Interpreter menjalankan model INT8
5. Output: array 3 float [prob_aman, prob_waspada, prob_bahaya]
6. Ambil argmax → label klasifikasi
7. Tampilkan overlay di UI dengan warna sesuai status
8. User bisa mengisi catatan dan mengirim laporan ke Supabase
```

---

## 5. Komponen 3: ML Pipeline

**Lokasi**: `backend/`  
**Teknologi**: Python 3.11, TensorFlow 2.15+, DVC, MLflow, Docker

### 5.1 Struktur

```
backend/
├── Dockerfile                      # Hermetic training environment
├── config/
│   ├── training.yaml               # Master config (single source of truth)
│   └── experiments/                # Experiment override YAML files
│       ├── v3a_baseline.yaml
│       ├── v3b_more_layers.yaml
│       ├── v3c_waspada_boost.yaml
│       └── v3d_higher_lr.yaml
├── data/
│   ├── raw/                        # Gambar mentah (DVC-tracked)
│   ├── processed/                  # Dataset teranotasi (DVC-tracked)
│   │   ├── AMAN/    (2009 img)
│   │   ├── WASPADA/  (768 img)
│   │   └── BAHAYA/   (767 img)
│   └── processed.dvc
├── src/
│   ├── android/
│   │   └── CrackClassifier.kt      # Referensi classifier untuk Android
│   └── training/
│       ├── train.py                # Training script (MLflow + callbacks)
│       ├── config_loader.py        # YAML → typed namespace
│       ├── augment.py              # In-graph augmentation layers
│       ├── evaluation.py           # Metrics, confusion matrix, ROC
│       └── export.py               # TFLite INT8 PTQ + benchmark
├── scripts/
│   ├── scraping/
│   │   └── image_scraper.py        # DuckDuckGo scraper (70+ keywords)
│   └── processing/
│       ├── validate_dataset.py     # Cek integritas gambar
│       ├── deduplicate.py          # Cross-class near-duplicate detection
│       ├── dataset_stats.py        # Statistik dataset
│       ├── split_dataset.py        # Stratified 70/15/15 split
│       └── organize_raw_data.py
├── tests/
│   ├── test_data.py                # 7 tests
│   ├── test_model.py               # 6 tests
│   └── test_export.py              # 3 tests
├── models/
│   ├── labels.txt                  # AMAN, WASPADA, BAHAYA
│   └── retak_mobilenetv2.tflite    # INT8 quantized (<5MB)
└── logs/
    ├── mlruns/                     # MLflow experiment tracking
    └── tensorboard/                # TensorBoard logs
```

### 5.2 Pipeline Stages (DVC)

```yaml
stages:
  scrape:       # Scrape gambar dari DuckDuckGo
    cmd: python scripts/scraping/image_scraper.py
  validate:     # Validasi integritas gambar
    cmd: python scripts/processing/validate_dataset.py
  train:        # Training + evaluasi + export
    cmd: python src/training/train.py
```

### 5.3 Makefile Targets

| Target | Deskripsi |
|--------|-----------|
| `make setup` | Install dependencies via uv |
| `make scrape` | Scraping gambar retakan |
| `make validate` | Validasi dataset |
| `make deduplicate` | Deteksi near-duplicate |
| `make stats` | Statistik dataset |
| `make split` | Stratified split 70/15/15 |
| `make train` | Training → evaluasi → export TFLite |
| `make test` | Jalankan 16 pytest |
| `make tune` | Hyperparameter grid search |
| `make cv` | 5-fold cross-validation |
| `make register` | Register model ke MLflow |
| `make deploy` | Copy model ke Android assets |
| `make docker-build` | Build Docker image |
| `make docker-train` | Training di dalam Docker |

---

## 6. Komponen 4: Backend (Supabase)

### 6.1 Mengapa Supabase?

- **Satu platform untuk semua**: Database, Auth, Storage, dan Realtime — tidak perlu bikin REST API terpisah
- **Gratis** untuk skala proyek lomba (500MB database, 1GB storage, 50K MAU)
- **Sudah terintegrasi** dengan Android app (Kotlin SDK) dan Web dashboard (JS SDK)
- **Realtime** — laporan baru langsung muncul di dashboard tanpa refresh

### 6.2 Skema Database

#### Tabel: `laporan`

```sql
CREATE TABLE laporan (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  nama_lokasi   TEXT NOT NULL,
  status        TEXT NOT NULL CHECK (status IN ('AMAN', 'WASPADA', 'BAHAYA')),
  catatan       TEXT DEFAULT '',
  latitude      DOUBLE PRECISION NOT NULL,
  longitude     DOUBLE PRECISION NOT NULL,
  foto_url      TEXT,
  pelapor       TEXT NOT NULL,
  terverifikasi INTEGER DEFAULT 0,
  created_at    TIMESTAMPTZ DEFAULT now()
);

-- Enable Realtime untuk notifikasi data baru
ALTER PUBLICATION supabase_realtime ADD TABLE laporan;
```

#### Row Level Security (RLS)

```sql
-- Izinkan SELECT untuk semua user (termasuk anon — untuk dashboard publik)
CREATE POLICY "Laporan dapat dibaca publik" ON public.laporan
  FOR SELECT USING (true);

-- Izinkan INSERT untuk authenticated user
CREATE POLICY "User dapat membuat laporan" ON public.laporan
  FOR INSERT WITH CHECK (auth.role() = 'authenticated');

-- Izinkan UPDATE untuk pemilik laporan
CREATE POLICY "User dapat update laporannya" ON public.laporan
  FOR UPDATE USING (auth.uid() = pelapor);
```

#### Storage Bucket: `laporan-foto`

```sql
-- Bucket untuk menyimpan foto retakan
INSERT INTO storage.buckets (id, name, public) VALUES ('laporan-foto', 'laporan-foto', true);

-- Policy: public read, authenticated insert
CREATE POLICY "Foto dapat dibaca publik" ON storage.objects
  FOR SELECT USING (bucket_id = 'laporan-foto');
CREATE POLICY "User dapat upload foto" ON storage.objects
  FOR INSERT WITH CHECK (bucket_id = 'laporan-foto' AND auth.role() = 'authenticated');
```

### 6.3 Kredensial

Kredensial Supabase disimpan di environment variables, **tidak di-commit ke git**:

**Android** (`local.properties`):
```properties
SUPABASE_URL=https://xxxxxxxxx.supabase.co
SUPABASE_ANON_KEY=eyJhbGciOiJI...
```

**Web** (`.env.local`):
```env
VITE_SUPABASE_URL=https://xxxxxxxxx.supabase.co
VITE_SUPABASE_ANON_KEY=eyJhbGciOiJI...
```

---

## 7. Data Pipeline & Versioning

### 7.1 Sumber Data

Gambar retakan tanah di-scrape dari DuckDuckGo Images dengan **70+ keyword** dalam bahasa Indonesia dan Inggris:

- `retakan tanah`, `tanah retak`, `longsor`, `landslide crack`, `soil crack`, `ground fissure`, dll.

### 7.2 Kualitas Data

| Filter | Parameter |
|--------|-----------|
| Resolusi minimum | 300×300 piksel |
| Blur detection | Laplacian variance < 80 → ditolak |
| Near-duplicate | Perceptual hash (pHash), Hamming distance < 6 → ditolak |
| Format | JPEG only, RGB |

### 7.3 Dataset Final

| Kelas | Jumlah Gambar | Proporsi |
|-------|-------------|----------|
| AMAN | 2,009 | 56.7% |
| WASPADA | 768 | 21.7% |
| BAHAYA | 767 | 21.6% |
| **Total** | **3,547** | 100% |

Dataset di-split secara stratified: **70% train, 15% validation, 15% test** dengan seed tetap (42).

### 7.4 DVC Workflow

```bash
# Pull dataset dari DagsHub
dvc pull

# Setelah menambah data baru
dvc add backend/data/processed
git add backend/data/processed.dvc
git commit -m "data: update dataset"
dvc push
git push
```

Remote DVC: `https://dagshub.com/jaweed3/retakId.dvc`

---

## 8. Model Machine Learning

### 8.1 Arsitektur

| Parameter | Nilai |
|-----------|-------|
| Base Model | MobileNetV2 (pre-trained ImageNet) |
| Input | 224 × 224 × 3 uint8 [0, 255] |
| Classification Head | GlobalAveragePooling2D → Dropout(0.3) → Dense(3, softmax) |
| Total Parameter | ~2.26M |
| Trainable | ~3.8K (head only, transfer learning) |

### 8.2 Training

| Parameter | Nilai |
|-----------|-------|
| Optimizer | Adam (lr=1e-4) |
| Loss | Categorical Crossentropy |
| Epochs | 50 max (EarlyStopping patience=10) |
| Batch Size | 32 |
| Class Weight | `balanced` (sklearn) |

### 8.3 Augmentasi (In-Graph)

| Teknik | Range |
|--------|-------|
| RandomFlip | Horizontal + Vertical |
| RandomRotation | ±30° |
| RandomZoom | ±20% |
| RandomTranslation | ±20% |
| RandomBrightness | 0.7–1.3× |
| RandomContrast | 0.8–1.2× |

### 8.4 Quantization

| Metrik | FP32 | INT8 |
|--------|------|------|
| Ukuran Model | ~8.5 MB | ~2.6 MB |
| Akurasi | ~85% | ~82% |
| Kecepatan Inferensi | ~120ms | ~45ms (di HP mid-range) |

---

## 9. Panduan Setup & Menjalankan

### 9.1 Web Dashboard

```bash
cd web-app

# 1. Install dependencies
npm install

# 2. Setup kredensial Supabase
cp .env.example .env.local
# Edit .env.local — isi VITE_SUPABASE_URL dan VITE_SUPABASE_ANON_KEY

# 3. Jalankan development server
npm run dev
# Buka http://localhost:5173

# 4. Build production
npm run build
# Output di dist/

# 5. Preview production build
npx vite preview
```

### 9.2 ML Pipeline

```bash
cd backend

# 1. Install dependencies
uv sync

# 2. Pull dataset
dvc pull

# 3. Training lengkap
make train

# 4. Testing
make test

# 5. Lihat hasil di MLflow
mlflow ui --backend-store-uri file://$(pwd)/logs/mlruns
```

### 9.3 Android App

```bash
git checkout mobile-app
# Buka di Android Studio
# Pastikan local.properties berisi SUPABASE_URL dan SUPABASE_ANON_KEY
# Build & run di emulator atau HP
```

---

## 10. Deployment

### 10.1 Web Dashboard → Vercel

```bash
cd web-app

# Deploy production
npx vercel --prod

# Set environment variables di Vercel dashboard:
# VITE_SUPABASE_URL
# VITE_SUPABASE_ANON_KEY
```

### 10.2 Android App → APK

```bash
cd mobile-app
./gradlew assembleRelease
# APK di app/build/outputs/apk/release/
```

### 10.3 ML Training → Docker

```bash
cd backend
make docker-build
make docker-train
```

---

## 11. Testing

### 11.1 ML Pipeline (16 tests)

```bash
cd backend && make test
```

| File | Tests | Cakupan |
|------|-------|---------|
| `test_data.py` | 7 | Validasi dataset, split, statistik, dedup, config |
| `test_model.py` | 6 | Build model, forward pass, augmentasi, seed |
| `test_export.py` | 3 | TFLite export, labels, benchmark |

### 11.2 Web Dashboard (TypeScript)

```bash
cd web-app
npx tsc --noEmit    # Type check
npm run build        # Build (gagal kalau ada error TS)
```

---

## 12. Tim & Peran

| Nama | Peran | Kontribusi |
|------|-------|-----------|
| **Farrel Ghozy** | Data Acquisition, Annotation & Web Dashboard | Scraping 3,547 gambar, anotasi manual, bangun web dashboard React+Supabase |
| **Adam Nurwahid** | Android Development | Aplikasi Android (CameraX, TFLite, Supabase integrasi, UI/UX) |
| **Jaweed (Fatih)** | ML Pipeline & Infrastructure | Training MobileNetV2, INT8 quantization, DVC/MLflow pipeline, Docker, hyperparameter tuning |

---

## 13. FAQ

### Q: Kenapa tidak pakai REST API sendiri (Express/Django/FastAPI)?

Supabase sudah menyediakan semua yang dibutuhkan (database, auth, storage, realtime) sebagai Backend-as-a-Service. Membuat REST API sendiri akan menambah kompleksitas dan memerlukan server yang harus di-maintain — tidak cocok untuk proyek lomba dengan sumber daya terbatas.

### Q: Kenapa web dashboard pakai React + Vite, bukan vanilla HTML/CSS/JS?

Dashboard memiliki 3 halaman dan 8+ komponen interaktif (map, filter, list, detail, realtime). React memberi component reusability, state management yang bersih, dan maintainability lebih baik. Vite sebagai build tool sangat cepat dibanding alternatif.

### Q: Kenapa peta pakai Leaflet, bukan Google Maps?

Leaflet **gratis dan open-source** — tidak perlu API key berbayar. Cukup untuk kebutuhan proyek ini: menampilkan titik marker dengan kustomisasi warna dan popup.

### Q: Apakah web dashboard butuh server?

**Tidak**. Dashboard dibangun sebagai Single Page Application (SPA) statis. Semua data diambil langsung dari browser ke Supabase via Supabase JS SDK. Vercel hanya menyajikan file HTML/CSS/JS statis.

### Q: Apakah aplikasi Android bisa jalan tanpa internet?

**Ya, untuk deteksi**. Inferensi TFLite berjalan sepenuhnya di perangkat. Koneksi internet hanya dibutuhkan untuk mengirim laporan ke Supabase — laporan bisa dikirim nanti saat sudah ada koneksi.

### Q: Bagaimana cara mendapatkan kredensial Supabase?

Kredensial Supabase (URL dan anon key) dapat dilihat di Supabase Dashboard → Project Settings → API. Hubungi Adam (Android dev) atau Fatih (ML lead) untuk akses.

---

## Referensi Tambahan

| Dokumen | Isi |
|---------|-----|
| [docs/architecture.md](docs/architecture.md) | Arsitektur teknis Edge-First |
| [docs/getting_started.md](docs/getting_started.md) | Panduan setup dari nol + Makefile reference |
| [docs/model_detail.md](docs/model_detail.md) | Detail model ML |
| [docs/inference_contract.md](docs/inference_contract.md) | Kontrak input/output TFLite untuk Android |
| [docs/scraping_guide.md](docs/scraping_guide.md) | Panduan scraping dan anotasi data |
| [docs/dvc_workflow.md](docs/dvc_workflow.md) | Workflow DVC untuk data versioning |
| [docs/android_integration.md](docs/android_integration.md) | Panduan integrasi TFLite ke Android |

---

_Dokumentasi terakhir diperbarui: 13 Mei 2026. Untuk pertanyaan, hubungi tim Retak.id._
