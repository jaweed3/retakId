# Scraping Guide — Retak.id

Mengumpulkan dataset retakan tanah menggunakan DuckDuckGo Image Search dengan
pipeline kualitas otomatis.

## Cara Pakai

### 1. Setup
```bash
make setup
```

### 2. Jalankan Scraper
```bash
make scrape KW="landslide soil cracks, retakan tanah longsor, ground fissure" LIMIT=150
```

Untuk keyword banyak (rekomendasi):
```bash
make scrape KW="landslide soil cracks, retakan tanah longsor, ground fissure, soil surface cracks landslide, soil crack texture, tanah retak, tanah merekah" LIMIT=150
```

### 3. Output
Gambar tersimpan di `backend/data/raw/<keyword>/`:
- File `.jpg` (nama UUID)
- `manifest.json` — metadata URL, pHash, status download

## Quality Pipeline (Otomatis)

Setiap gambar yang didownload melalui 3 filter:

| Filter | Parameter | Keterangan |
|--------|-----------|------------|
| **Duplicate Detection** | pHash exact match | Tolak gambar yang sudah ada |
| **Near-Duplicate** | Hamming distance < 6 | Tolak gambar yang mirip (beda resolusi/URL) |
| **Blur Detection** | Laplacian variance < 100 | Tolak gambar buram |
| **Size Check** | Min 200×200 px | Tolak gambar terlalu kecil |

## Rekomendasi Keywords

Untuk hasil akurat (retakan tanah, bukan aspal/tembok):
- `"landslide soil fissure"`
- `"ground cracks earth movement"`
- `"retakan tanah bukit"`
- `"soil surface cracks landslide"`
- `"deep ground fissure"`
- `"soil crack texture"`
- `"tanah retak"`
- `"tanah merekah"`
- `"tanah longsor"`
- `"retakan lereng"`

**Tips:** Keyword yang general (tanpa "tambang") menghasilkan gambar lebih banyak dan
lebih beragam. Model mengklasifikasi severity retakan, bukan penyebab — jadi variasi
visual lebih penting daripada konteks tambang.

## Langkah Selanjutnya (Anotasi Manual)

Setelah gambar di-download:
1. Sortir gambar yang tidak relevan (aspal, tembok, bukan tanah)
2. Pindahkan ke `backend/data/processed/`:
   - `AMAN/` — retakan kecil/halus, tanah masih stabil
   - `WASPADA/` — retakan sedang, mulai membesar
   - `BAHAYA/` — retakan besar/dalam, tanah terbelah
3. Target: minimal 100–200 gambar per kelas
4. Jalankan `make validate` untuk cek kualitas akhir

## Data Versioning (DVC)

Setelah anotasi selesai, gunakan DVC untuk share dataset dengan tim:
```bash
dvc add backend/data/processed/
git add backend/data/.gitignore backend/data/processed.dvc
git commit -m "data: annotated dataset v1"
dvc push
```

Lihat `docs/dvc_workflow.md` untuk panduan lengkap.
