# Scraping Guide for Retak.id

Tool ini dirancang untuk mengumpulkan data tambahan secara cepat menggunakan DuckDuckGo Image Search.

## Cara Pakai

1.  **Setup Environment**:
    Pastikan sudah install dependencies:
    ```bash
    make setup
    ```

2.  **Jalankan Scraper**:
    Gunakan command `make scrape` dengan argumen `KW` (Keywords) dan `LIMIT` (Jumlah gambar per keyword).
    ```bash
    make scrape KW="landslide soil cracks, retakan tanah longsor" LIMIT=150
    ```

3.  **Output**:
    Gambar akan tersimpan di `backend/data/raw/<keyword_name>/`.
    Setiap folder akan berisi:
    -   File gambar dalam format `.jpg` (nama random UUID).
    -   `manifest.json`: Berisi metadata URL asal untuk tracking jika diperlukan.

## Rekomendasi Keywords
Untuk mendapatkan hasil yang akurat (menghindari aspal/tembok):
-   "landslide soil fissure"
-   "ground cracks earth movement"
-   "retakan tanah bukit"
-   "soil surface cracks landslide"

## Cara Modifikasi
Jika ingin mengubah logic scraping, buka `backend/scripts/scraping/image_scraper.py`:
-   **Parallelism**: Ubah `max_workers=10` di `ThreadPoolExecutor` jika koneksi sangat cepat/lambat.
-   **Image Size**: Ubah `size="Medium"` menjadi `"Large"` di `ddgs.images()` jika butuh resolusi lebih tinggi (namun loading akan lebih lama).
-   **Validation**: Saat ini script otomatis convert ke RGB JPG. Jika ingin format lain, ubah bagian `img.save()`.

## Langkah Selanjutnya (Anotasi)
Setelah gambar didownload:
1.  Sortir manual gambar yang tidak relevan.
2.  Pindahkan ke folder `backend/data/processed/AMAN`, `WASPADA`, atau `BAHAYA`.
