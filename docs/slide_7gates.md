# 7 Safety Gate — Validasi Model Sebelum Deploy

> Slide ini muncul setelah ML Pipeline (slide 6), sebagai bukti bahwa model
> tidak asal deploy — ada quality gate otomatis.

---

## Teks untuk Slide

**Judul: 7 Lapis Validasi — Gagal Satu, Tidak Deploy**

```
┌──────────────────────────────────────────────────┐
│  GATE 1: Load Test                               │
│  Model TFLite bisa dibaca + dialokasikan         │
│  tanpa error                                     │
├──────────────────────────────────────────────────┤
│  GATE 2: Input Shape                             │
│  uint8 [1, 224, 224, 3] — sesuai kontrak Android │
├──────────────────────────────────────────────────┤
│  GATE 3: Output Spec                             │
│  float32 [1, 3] — 3 kelas (AMAN/WASPADA/BAHAYA) │
├──────────────────────────────────────────────────┤
│  GATE 4: Inference Run                           │
│  Model jalan di gambar uji — tidak crash         │
├──────────────────────────────────────────────────┤
│  GATE 5: Multi-Class                             │
│  Memprediksi ≥ 2 kelas — tidak stuck di 1 kelas  │
├──────────────────────────────────────────────────┤
│  GATE 6: Confidence Quality                      │
│  Max confidence > 45% · Std dev > 2%             │
│  (tidak flat 33/33/33)                           │
├──────────────────────────────────────────────────┤
│  GATE 7: Benchmark Threshold                     │
│  BAHAYA recall ≥ 72% · Akurasi ≥ 82%             │
│  Macro F1 ≥ 0.75                                 │
└──────────────────────────────────────────────────┘
```

### Footer (bisa kecil di bawah):

```
✅ Semua lulus → deploy ke model registry + delta OTA
❌ Satu gagal → blokir deploy, harus fix dulu
```

---

## Catatan Presenter

| Gate | Cara cek | Kode |
|------|----------|------|
| 1 | `tf.lite.Interpreter(model_path)` | `scripts/validate_model.py:48` |
| 2 | `input_details["shape"] == [1,224,224,3]` | `scripts/validate_model.py:67` |
| 3 | `output_details["shape"] == [1,3]` | `scripts/validate_model.py:91` |
| 4 | `interpreter.invoke()` on test images | `scripts/validate_model.py:132-133` |
| 5 | `len(unique_preds) >= 2` | `scripts/validate_model.py:168` |
| 6 | `max_conf > 0.45 && std_conf > 0.02` | `scripts/validate_model.py:183-199` |
| 7 | Cross-val score ≥ threshold | `scripts/cross_validate.py` → `Makefile` |

### Script yang user (jaweed) —> jalankan sebelum deploy:
```bash
uv run python scripts/validate_model.py
# Kalau lulus → lanjut deploy
# Kalau gagal → exit code 1, DO NOT DEPLOY
```

---

## Kenapa 7 Gates Ini Penting?

**Gate 1–3:** Cek kontrak input/output. Model yang berubah shape tanpa
pemberitahuan akan nge-crash Android app. Ini safety paling dasar.

**Gate 4–6:** Cek model tidak "frozen" — pernah terjadi model setelah
quantisasi cuma prediksi 1 kelas. Gate 5 nangkep itu.

**Gate 7:** Cek performa numerik. BAHAYA recall ≥ 72% adalah prioritas
#1 — false negative paling berbahaya untuk sistem kami.
