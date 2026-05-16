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

> **Visual:** Pie chart 5 faktor (ML 50%, Slope 20%, Rain 15%, Elev 10%, Soil 5%) + kotak contoh hitung

### Teks di slide:

**Judul: Bukan Cuma Foto — 5 Faktor Risiko Real-Time**

```
PIE CHART (lingkaran):
  🧠 ML Visual        50%
  ⛰️ Kemiringan Lereng 20%
  🌧️ Curah Hujan       15%
  🏔️ Ketinggian        10%
  🪨 Jenis Tanah        5%
```

**Kotak contoh di samping pie chart:**

```
ML bilang AMAN (confidence 90%)        = 0.1 × 50%
Tapi lereng 30°                        = 1.0 × 20%
Hujan deras 35mm                       = 1.0 × 15%
────────────────────────────────────────
FINAL SCORE = 0.42 → WASPADA ✅
```

**Footer:**
```
AMAN floor = 0.1   ·   Graceful degradation ✓
ML prediksi bisa di-override lingkungan
Area tanpa laporan tetap terdeteksi via slope + hujan
```

**Key message:** *"ML hanya 50%. Lereng curam + hujan deras tetap dapat skor tinggi meskipun tanpa laporan warga. Aman tidak pernah nol."*

---

## Slide 6: ML Pipeline (1 menit)

> **Visual:** Timeline chart + tabel + 7 checklist

### Teks di slide:

**Judul: MobileNetV2 INT8 — 84.9% Akurasi, 2.6 MB, <50ms**

```
INPUT:  uint8 [1, 224, 224, 3] RGB
  → MobileNetV2 backbone (fine-tune layer 130+)
  → Global Average Pooling → Dense(3) → softmax
OUTPUT: float32 [1, 3] logits → AMAN / WASPADA / BAHAYA
```

**Tabel dataset:**

| Kelas | Sampel |
|-------|--------|
| AMAN | 2,009 |
| WASPADA | 768 |
| BAHAYA | 767 |
| **Total** | **3,547** |

**Safety Gate — 7 checks sebelum deploy:**

```
✅ Load test     ✅ Input shape    ✅ Output dtype
✅ Inference OK  ✅ Multi-class    ✅ Confidence
✅ Cross-validation ≥ threshold
```

**Key message:** *"84.9% akurasi, 2.6 MB, <50ms inferensi. Setiap model baru harus lewat 7 gates sebelum menggantikan yang lama."*

---

## Slide 7: Continuous Improvement — Verifikasi → Retrain (1 menit)

> **Visual:** Flowchart melingkar

### Teks di slide:

**Judul: Setiap Verifikasi Jadi Data Training**

```
DIAGRAM LINGKARAN (6 langkah):
  ┌─ ① Warga Lapor ─→ ② ML Predict ─→ ③ Admin Verifikasi ─┐
  └──── ⑥ Retrain ←── ⑤ Ingest CSV ←── ④ Export CSV ──────┘
```

**Kotak 3 poin di samping diagram:**

```
✓ Admin klik "Sesuai" → label = prediksi ML
✓ Admin klik "Tidak" → pilih label benar (AMAN/WASPADA/BAHAYA)
✓ Keduanya jadi data training — tidak ada yang terbuang
```

**Footer:**
```
Pipeline: Export CSV → ingest_verification.py (phash dedup)
→ make split && make train → Model v3b, v3c, ...
```

**Key message:** *"Setiap verifikasi — benar atau salah — jadi data training. Tidak ada yang terbuang. Model terus belajar dari koreksi staf BPBD."*

---

## Slide 8: Delta OTA — Update Model Hemat Kuota (45 detik)

> **Visual:** Dua kotak ukuran bersanding

### Teks di slide:

**Judul: Update Model 70–90% Lebih Kecil**

```
┌──────────────────────────────────┐
│  FULL MODEL                      │
│  █████████████████████████████   │
│  2.6 MB — download semuanya     │
└──────────────────────────────────┘
┌──────────────────────────────────┐
│  DELTA OTA (.rkd)                │
│  ████░░░░░░░░░░░░░░░░░░░░░░░░░  │
│  0.3–0.8 MB — byte-diff + gzip  │
└──────────────────────────────────┘
```

**Cara kerja:**
```
compute_delta.py → .rkd → upload Supabase
    ↓
HP download .rkd → gzip decompress → patch byte regions
    ↓
Validasi TFLite Interpreter → save ke internal storage
```

**3 lapis safety:**
```
1. Patch dari bundled assets (APK), bukan cached model
2. Validasi Interpreter sebelum save
3. Full model fallback kalo delta gagal
```

**Key message:** *"Warga di desa terpencil punya kuota terbatas. Update 2.6 MB full model seminggu sekali tidak realistis. Delta 0.5 MB bisa."*

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
