# Retak.id — On-Device Crack Detection with Multi-Factor Environmental Risk Scoring

Retak.id is a crowdsourcing platform for early detection of landslide soil cracks in Jenangan, Ponorogo. Combines on-device ML with environmental context (elevation, slope, rainfall, soil type) for more accurate risk assessment.

## Project Structure
- `mobile-app/`: Android Application (Kotlin + CameraX + TFLite + Firebase)
- `backend/`: ML Pipeline (Scraping, Training, Quantization)
- `docs/`: Technical documentation

## Tech Stack
- **Mobile:** Kotlin, Jetpack Compose, CameraX, Firebase
- **ML:** MobileNetV2, TensorFlow Lite (INT8 Quantized, 2.6MB)
- **Risk Factors:** Elevation, Slope, Rainfall, Soil Type (multi-factor weighted scoring)
- **Data Sources:** Open-Meteo API, ISRIC SoilGrids, GPS + EXIF metadata

## Features
- On-device TFLite inference (AMAN / WASPADA / BAHAYA)
- GPS + EXIF altitude extraction
- Elevation + slope calculation (Open-Meteo API / EXIF)
- Real-time weather risk overlay (Open-Meteo)
- Soil type classification (ISRIC SoilGrids API + fallback)
- Multi-factor weighted risk scoring engine
- Factor breakdown UI (expandable)
- Community report feed + map (Firebase Firestore)

## Scoring Formula
```
Final Risk = (ML × 50%) + (Slope × 20%) + (Rain × 15%) + (Elevation × 10%) + (Soil × 5%)
```

## Quick Links
- [Multi-Factor Risk Docs](docs/multi_factor_risk.md)
- [Getting Started](docs/getting_started.md)
- [Architecture](docs/architecture.md)
- [Model Details](docs/model_detail.md)
- [Q&A Defense](docs/qa_defense.md)
