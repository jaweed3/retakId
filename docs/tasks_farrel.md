# Tugas Farrel — Data Acquisition & Annotation

## Target
Mengumpulkan **600+ gambar retakan tanah** (minimal 200 per kelas: AMAN, WASPADA, BAHAYA),
memvalidasi kualitas, menganotasi, dan push ke DagsHub via DVC.

> Ini fondasi seluruh project. Model sebaik apapun arsitekturnya, kalo data jelek → hasil jelek.

---

## Checklist Pekerjaan

### Phase 1 — Scraping (Issue #1 — done, tinggal run)
- [ ] Jalankan scraper dengan 8-10 keyword:
  ```bash
  make scrape KW="landslide soil cracks, retakan tanah longsor, ground fissure, soil surface cracks landslide, soil crack texture, tanah retak, tanah merekah, surface ground cracks, deep soil fissure" LIMIT=150
  ```
- [ ] Target: ~1000-1500 gambar raw di `backend/data/raw/`
- [ ] Scraper akan otomatis: deduplicate (pHash), reject blur, reject <200px

### Phase 2 — Anotasi Manual (Issue #2)
- [ ] Buka `backend/data/raw/<keyword>/`, sortir gambar satu per satu
- [ ] Pindahkan ke `backend/data/processed/`:
  ```
  AMAN/      → retakan kecil/halus, tanah masih stabil, retak rambut
  WASPADA/   → retakan sedang, mulai membesar, perlu perhatian
  BAHAYA/    → retakan besar/dalam, tanah terbelah, jelas berbahaya
  ```
- [ ] **BUANG** gambar yang:
  - Bukan tanah (aspal, tembok, beton, keramik)
  - Ada manusia/hewan dominan
  - Text/watermark/screenshot
  - Retakan bukan di tanah (tembok retak ≠ tanah retak)
- [ ] Target minimum: **200 gambar per kelas** (total 600+)
- [ ] Ideal: 300-500 per kelas kalo memungkinkan

### Phase 3 — Validasi (Issue #16, #17, #27)
- [ ] Cek kualitas dataset:
  ```bash
  make validate     # pastiin ga ada corrupt
  make deduplicate  # cek cross-class duplicate
  make stats        # liat distribusi
  ```
- [ ] Kalo `make deduplicate` nemu cross-class duplicate → hapus dari kelas yang lebih banyak
- [ ] Pastiin class distribution ga timpang banget (max 5:1)

### Phase 4 — Push ke DagsHub via DVC (Issue baru)
- [ ] Setup DVC credential:
  ```bash
  # Bikin token di https://dagshub.com/user/settings/tokens
  dvc remote modify dagshub --local auth basic
  dvc remote modify dagshub --local user <username-dagshub-lo>
  dvc remote modify dagshub --local password <token>
  ```
- [ ] Track dan push data:
  ```bash
  dvc add backend/data/raw/
  dvc add backend/data/processed/
  git add backend/data/.gitignore backend/data/*.dvc
  git commit -m "data: add annotated dataset v1"
  dvc push
  git push origin main
  ```
- [ ] Verifikasi: cek DagsHub UI, pastiin file muncul

### Phase 5 — Dokumentasi Dataset (opsional tapi nilai plus)
- [ ] Catat source keyword yang paling banyak ngasih gambar bagus
- [ ] Catat jumlah final per kelas
- [ ] Screenshot contoh per kelas (buat slide deck)

---

## Tips Biar Menang

### 1. Kualitas > Kuantitas
Juri ga akan tau lo punya 1000 gambar. Tapi mereka akan liat akurasi model.
- 200 gambar berkualitas > 500 gambar asal-asalan
- Pastiin tiap gambar BENERAN retakan tanah, bukan aspal/tembok
- Lebih baik 150 gambar bersih per kelas daripada 500 gambar noisy

### 2. Variasi itu kunci
Model harus generalize. Kalo semua gambar AMAN dari tempat yang sama → overfit.
- Variasi lighting: pagi, siang, sore (kalo ada)
- Variasi angle: close-up, medium shot, wide shot
- Variasi jenis tanah: tanah merah, tanah hitam, tanah kering, tanah basah
- Variasi lokasi: kalo bisa 3-5 lokasi berbeda

### 3. Konsistensi anotasi
Sebelum mulai, define bareng Jaweed:
- Contoh gambar AMAN kayak apa
- Contoh gambar WASPADA kayak apa
- Contoh gambar BAHAYA kayak apa
- Taruh 3-5 contoh di folder `docs/examples/` biar konsisten

Kalo ragu di border case (antara WASPADA dan BAHAYA), pilih yang lebih parah.
**Better false positive (BAHAYA padahal WASPADA) daripada false negative.**

### 4. "Retakan" bukan "Tambang"
Keyword "retakan tambang" atau "illegal mining crack" terlalu sempit.
Yang dicari: **retakan tanah**, apapun penyebabnya. Model ga peduli penyebab —
dia cuma liat severity retakan. Jadi keyword general justru lebih baik.

### 5. Manifest.json itu berguna
Setiap folder hasil scrape ada `manifest.json`. Kalo nanti ada gambar
yang mencurigakan (misal hasil bagus banget/aneh), bisa trace balik ke URL source.
Ini penting buat kredibilitas dataset kalo ditanya juri.

---

## Cara Cepet Sortir (Workflow Efisien)

### Mac: Quick Look + Drag & Drop
```bash
# Buka 2 Finder window side by side:
# Kiri: backend/data/raw/<keyword>/
# Kanan: backend/data/processed/AMAN/
# 
# 1. Klik gambar di kiri, tekan SPACE (Quick Look)
# 2. Arrow key untuk navigasi
# 3. Drag ke folder kanan sesuai kelas
# 4. Kalo ga relevan: CMD+DELETE
```

### Tips Kecepatan
- Jangan overthink. Kalo 3 detik ga bisa mutusin, kasih ke WASPADA
- Setelah 50 gambar, istirahat 2 menit. Anotasi fatigue itu real
- Target: 100 gambar/jam. Total effort: ~6 jam untuk 600 gambar
