# DVC Data Workflow — Retak.id

Remote: [DagsHub](https://dagshub.com/jaweed3/retakId) (S3-compatible, free tier)

## Setup (sekali aja)

```bash
# Install DVC
pip install dvc

# Remote sudah dikonfigurasi. Verifikasi:
dvc remote list
# dagshub  https://dagshub.com/jaweed3/retakId.dvc
```

### Auth (diperlukan untuk push)

Bikin token DagsHub:
1. Buka https://dagshub.com/user/settings/tokens
2. Create token dengan scope `dvc`
3. Set credential:

```bash
dvc remote modify dagshub --local auth basic
dvc remote modify dagshub --local user <username-dagshub>
dvc remote modify dagshub --local password <token-dagshub>
```

Ini disimpan di `.dvc/config.local` (gitignored, aman).

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
git commit -m "data: add scraped and annotated dataset"

# 4. Push data ke DagsHub remote
dvc push

# 5. Push git metadata
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
| `dvc push` | Upload tracked data to DagsHub remote |
| `dvc pull` | Download tracked data from DagsHub remote |
| `dvc status` | Check if local data matches remote |
| `dvc remote list` | List configured remotes |
