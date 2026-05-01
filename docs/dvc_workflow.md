# DVC Data Workflow — Retak.id

## Setup (sekali aja)

```bash
# Install DVC
pip install dvc

# Setup Google Drive remote
# 1. Buka https://drive.google.com, bikin folder "retakid-dvc"
# 2. Dapetin folder ID dari URL: https://drive.google.com/drive/folders/<FOLDER_ID>
# 3. Set remote:
dvc remote add -d gdrive gdrive://<FOLDER_ID>
```

## Push Data (Farrel — setelah scraping & anotasi)

```bash
# 1. Pastikan data ada di folder yang bener
ls backend/data/raw/           # hasil scrape
ls backend/data/processed/     # hasil anotasi (AMAN/ WASPADA/ BAHAYA/)

# 2. Track data dengan DVC
dvc add backend/data/raw/
dvc add backend/data/processed/

# 3. Commit DVC metadata ke git
git add backend/data/.gitignore backend/data/*.dvc
git commit -m "data: add scraped and annotated dataset v1"

# 4. Push data ke remote
dvc push

# 5. Push git
git push origin main
```

## Pull Data (ML engineer — setelah Farrel push)

```bash
git pull origin main
dvc pull
# Data sekarang ada di backend/data/raw/ dan backend/data/processed/
```

## Quick Reference

| Command | What it does |
|---------|-------------|
| `dvc add <path>` | Track a file/folder with DVC (replaces with pointer) |
| `dvc push` | Upload tracked data to remote storage |
| `dvc pull` | Download tracked data from remote storage |
| `dvc status` | Check if local data matches remote |
| `dvc remote list` | List configured remotes |
