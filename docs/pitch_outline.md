# Pitch Presentation Outline — IYREF 2026 Semi-Final

**Durasi:** 10 menit presentasi + 5 menit Q&A
**Format:** Slide + Live Demo (opsional)
**Tone:** Problem-focussed → Solution → Evidence → Ask

---

## Slide 1: Opening Hook (30 detik)

> **Visual:** Foto retakan tanah + longsor + jalan terputus (before/after)
>
> **Narator:**
> *"Jenangan, Ponorogo. 41 longsor dalam 4 bulan. Tambang ilegal. Jalan putus. BPBD butuh data lapangan real-time yang tidak bisa diberikan IoT sensor atau satelit — terlalu mahal, tidak menjangkau desa terpencil."*
>
> **Tagline muncul:**
> *"Kami percaya setiap warga dengan smartphone bisa deteksi longsor sebelum terjadi."*

---

## Slide 2: The Problem (1 menit)

> **Visual:** Peta Jenangan + animasi titik longsor + foto kerusakan

| Poin | Script |
|------|--------|
| **Latar** | Jenangan, Ponorogo — desa agraris di lereng perbukitan |
| **Penyebab** | Tambang ilegal → vegetasi hilang → lereng tidak stabil |
| **Dampak** | 41 longsor, akses terputus, isolasi komunitas |
| **Gap** | BPBD tidak punya data real-time; IoT mahal (Rp 50jt+/unit), satelit resolusi rendah, drone butuh pilot + izin + tidak bisa terbang saat hujan |
| **Angka** | Musim hujan: intensitas tinggi, lereng kritis, korban jiwa |

**Transisi:** *"Solusi yang ada mahal dan tidak menjangkau. Kami membalik pertanyaannya: bagaimana kalau setiap warga jadi sensor?"*

---

## Slide 3: Solusi — Retak.id (1 menit)

> **Visual:** Mockup HP + dashboard side-by-side
> HP: Buka app → potret → hasil instan
> Dashboard: Peta dengan pin berwarna

| Poin | Script |
|------|--------|
| **Apa** | Platform crowdsourcing deteksi retakan tanah — Android + Web |
| **Cara** | Warga potret retakan → ML on-device → skor multi-faktor → dashboard BPBD |
| **Mengapa beda** | On-device ML (tanpa internet), offline-first, multi-faktor risiko, biaya Rp 0 |
| **Tiga pilar** | ① Nol infrastruktur ② Cakupan hiperlokal ③ Advokasi berbasis data |

---

## Slide 4: Demo — Flow Pelaporan (1.5 menit)

> **Visual:** Live demo atau screen recording — 3 skenario

### Skenario 1: Jalan Normal (AMAN)
```
Warga buka app → potret jalan aspal → ML: AMAN (92%)
→ MultiFactorEngine: slope 5°, hujan ringan → AMAN
→ Submit → pin hijau di dashboard
```

### Skenario 2: Retakan Berbahaya (BAHAYA)
```
Warga buka app → potret retakan lebar → ML: BAHAYA (85%)
→ MultiFactorEngine: slope 28°, hujan deras, tanah Vertisol
    → ML(0.50×0.85) + Slope(0.20×1.0) + Rain(0.15×1.0)
    + Elev(0.10×0.4) + Soil(0.05×1.0) = 0.815
→ ⚠️ BAHAYA → langsung muncul di dashboard
→ Notifikasi Telegram ke BPBD
```

### Skenario 3: Admin Verifikasi
```
Staf BPBD buka dashboard → klik laporan BAHAYA
→ VerificationDialog muncul: foto, ML result, confidence
→ Admin klik "Sesuai" → tersimpan ke riwayat
→ Export CSV mingguan → make split && make train
→ Model v3b lebih akurat
```

---

## Slide 5: Multi-Factor Risk Engine (1 menit)

> **Visual:** Diagram pie 5 faktor + animasi kalkulasi

```
╔══════════════════════════════════════╗
║        MultiFactorRiskEngine         ║
╠══════════════════════════════════════╣
║  🧠 ML Visual      50%  ← TFLite   ║
║  ⛰️ Slope           20%  ← Open-Meteo║
║  🌧️ Rainfall        15%  ← Open-Meteo║
║  🏔️ Elevation       10%  ← SRTM     ║
║  🪨 Soil Type        5%  ← ISRIC    ║
╠══════════════════════════════════════╣
║  AMAN floor score = 0.1              ║
║  Graceful degradation ✓              ║
╚══════════════════════════════════════╝
```

**Key message:** *"ML hanya 50%. Lereng curam + hujan deras tetap dapat skor tinggi meskipun tanpa laporan warga. Aman tidak pernah nol."*

---

## Slide 6: ML Pipeline (1 menit)

> **Visual:** Timeline / evolution chart

### Dataset
| Kelas | Sampel | Sumber |
|-------|--------|--------|
| AMAN | 2,009 | Scraping + anotasi manual |
| WASPADA | 768 | Scraping + anotasi manual |
| BAHAYA | 767 | Scraping + anotasi manual |
| **Total** | **3,547** | 70+ query pencarian |

### Training Evolution
```
Baseline          73.0%
+ Fine-tune       76.7%
+ Conservative FT 81.8%
+ Clean Labels    84.9%  ← v3a (PRODUKSI)
```

### Safety Gate
```
7 pemeriksaan sebelum deploy:
✓ Load test  ✓ Shape  ✓ Dtype  ✓ Infer
✓ Multi-class  ✓ Confidence  ✓ CV threshold
```

**Key message:** *"84.9% akurasi, 2.6 MB, <50ms inferensi. Setiap model baru harus lewat 7 gates sebelum menggantikan yang lama."*

---

## Slide 7: Continuous Improvement Loop (1 menit)

> **Visual:** Flowchart melingkar

```
LAPORAN MASUK → ML PREDICT → ADMIN VERIFIKASI
       ↑                              ↓
   RETRAIN ←── INGEST ←── EXPORT CSV
```

### Alur Lengkap
1. **Warga lapor** → ML + MultiFactorEngine → skor risiko
2. **Admin verifikasi** → VerificationDialog: "Sesuai?" / pilih label benar
3. **Data tersimpan** → `riwayat_penanganan` dengan `label_akhir`
4. **Export CSV** → `fetchTrainingData()` → CSV dengan foto URL
5. **Ingest** → `ingest_verification.py` → download foto + dedup phash
6. **Retrain** → `make split && make train` → Model baru

**Key message:** *"Setiap verifikasi — benar atau salah — jadi data training. Tidak ada yang terbuang. Model terus belajar dari koreksi staf BPBD."*

---

## Slide 8: Delta OTA — Update Tanpa Boros Kuota (45 detik)

> **Visual:** Perbandingan ukuran download

```
Full model:  ██████████████████████████  2.6 MB
Delta OTA:   ████░░░░░░░░░░░░░░░░░░░░░░  0.3–0.8 MB
                                      └── 70–90% lebih kecil
```

**Cara kerja:**
1. Compute delta: byte-diff antara model lama + baru → gzip → `.rkd`
2. Upload ke Supabase Storage + register versi
3. HP download `.rkd` → patch byte regions → validasi TFLite → simpan

**Kenapa penting:** *"Warga di desa terpencil punya kuota terbatas. Update 2.6 MB full model seminggu sekali tidak realistis. Delta 0.5 MB bisa."*

---

## Slide 9: Hasil & Dampak (1 menit)

> **Visual:** Infografis 4 kotak

```
┌─────────────────┐  ┌─────────────────┐
│  Akurasi Model   │  │  Waktu Respons  │
│     84.9%       │  │   < 300ms       │
│  Target: ≥ 82%  │  │  (ML + 5 faktor)│
└─────────────────┘  └─────────────────┘
┌─────────────────┐  ┌─────────────────┐
│  Ukuran Model    │  │  Biaya Operasional│
│     2.6 MB      │  │  ~$50/bln       │
│  INT8 quantized │  │  (Supabase cloud)│
└─────────────────┘  └─────────────────┘
```

**Dampak non-teknis:**
- Warga jadi **risk-aware** — tau tingkat bahaya lokasi mereka
- BPBD punya **data prioritas** — tidak perlu survei manual
- **Edukasi komunitas** — setiap laporan membangun literasi risiko
- **Advokasi berbasis data** — agregasi laporan jadi bukti untuk pemda

---

## Slide 10: Scaling & Roadmap (30 detik)

> **Visual:** Roadmap timeline

```
TAHAP 1 (Sekarang) ─── TAHAP 2 (2025) ─── TAHAP 3 (2026)
  ✓ Crowdsourcing       📋 Drone spot-      📡 Citra satelit
  ✓ ML on-device          check blind spot    Sentinel-2 InSAR
  ✓ MultiFactorEngine   📋 WhatsApp          📋 AI generatif
  ✓ HITL Verifikasi       chatbot hibrida     laporan otomatis
  ✓ Delta OTA           📋 Kerjasama BMKG    📋 Integrasi BNPB
                          + Destana           early warning
```

---

## Slide 11: Tim (15 detik)

> **Visual:** Foto tim + logo universitas

| Peran | Anggota |
|-------|---------|
| **ML Engineer** | Jaweed (Fatih) — pipeline, training, delta OTA |
| **Data & Web** | Farrel Ghozy — scraping, dashboard, edge functions |
| **Android Dev** | Adam Nurwahid — Kotlin, CameraX, risk engine |

**Universitas Darussalam Gontor, Ponorogo**

---

## Slide 12: Penutup — Call to Action (15 detik)

> **Visual:** Layar hitam + teks putih

*"41 longsor dalam 4 bulan. Warga jadi sensor. Data jadi aksi."*

**retak.id** — Crowdsourcing Early Detection of Landslide Soil Cracks

---

## Appendix: Q&A Preparation

### Topik 1: False Negative & HITL
> *"Sistem rentan false negative. Bagaimana verifikasi staf BPBD sekaligus jadi pelabelan data?"*

**Jawaban:**
- Pipeline loop tertutup: Verifikasi → Export CSV → Ingest → Retrain
- Setiap verifikasi (BENAR/SALAH) jadi data training — tidak ada yang hilang
- Admin koreksi label langsung di VerificationDialog
- Detail lengkap di `docs/verify_retrain.md`

### Topik 2: Blind Spot & Drone
> *"Area tanpa laporan warga bagaimana? Kenapa tidak pakai drone?"*

**Jawaban:**
- MultiFactorRiskEngine tetap hitung risiko dari slope + hujan + elevasi + tanah — 45% bobot tanpa laporan
- Drone: Rp 40-80jt, tidak bisa terbang hujan (risiko tertinggi), retakan ketutup vegetasi
- Alternatif: Kader Destana — gratis, sudah ada, patroli blind spot
- Detail lengkap di `docs/coverage_bias.md`

### Topik 3: Aplikasi vs WhatsApp
> *"Kenapa bikin aplikasi? WhatsApp lebih gampang."*

**Jawaban:**
- WhatsApp untuk notifikasi, Retak.id untuk deteksi (ML on-device + GPS + 5 faktor risiko)
- PWA: browser langsung, nol install, ML jalan via Wasm
- Urgensi instan: keputusan "lewat atau tidak" terjadi di detik, bukan jam
- Detail lengkap di `docs/adoption_access.md`

### Topik 4: Akurasi & Data
> *"Dataset hanya 3,547 — cukup?"*

**Jawaban:**
- MobileNetV2 transfer learning efektif dengan ribuan gambar; 84.9% akurasi dengan dataset ini
- Setiap verifikasi BPBD otomatis nambah dataset — model belajar terus
- Perceptual hash dedup mencegah duplikasi / label noise

### Topik 5: Biaya & Keberlanjutan
> *"Biaya operasional? Siapa yang bayar server?"*

**Jawaban:**
- Supabase free tier cukup untuk skala kabupaten ($0)
- ~$50/bln untuk scale ke kecamatan (storage + edge functions)
- Model fully on-device — beban server minimal
- Open source — bisa di-deploy mandiri oleh Pemda

---

## Timing Summary

| Slide | Durasi | Akumulasi |
|-------|--------|-----------|
| 1 — Hook | 30 detik | 0:30 |
| 2 — Problem | 1 menit | 1:30 |
| 3 — Solusi | 1 menit | 2:30 |
| 4 — Demo Flow | 1.5 menit | 4:00 |
| 5 — Risk Engine | 1 menit | 5:00 |
| 6 — ML Pipeline | 1 menit | 6:00 |
| 7 — Retrain Loop | 1 menit | 7:00 |
| 8 — Delta OTA | 45 detik | 7:45 |
| 9 — Hasil | 1 menit | 8:45 |
| 10 — Roadmap | 30 detik | 9:15 |
| 11 — Tim | 15 detik | 9:30 |
| 12 — Penutup | 15 detik | 9:45 |
| **Buffer** | **15 detik** | **10:00** |

---

## Files Referenced

| Doc | Untuk |
|-----|-------|
| `docs/model_detail.md` | Detail ML pipeline, training config, safety gates |
| `docs/verify_retrain.md` | HITL → retrain loop counter-attack |
| `docs/coverage_bias.md` | Blind spot + drone counter-attack |
| `docs/adoption_access.md` | App vs WhatsApp counter-attack |
| `docs/mitigation_false_negative.md` | 25-layer defense-in-depth |
| `docs/minimum_spec.md` | Minimum device specs |
| `docs/multi_factor_risk.md` | Risk engine architecture |
| `README.md` | Project overview & tech stack |
