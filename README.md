# Retak.id: On-Device Crack Detection MVP

MVP implementation for IYREF 2026 Semi-Final. Retak.id is an Android-based crowdsourcing app for early detection of landslide soil cracks in Jenangan, Ponorogo.

## Project Structure
- `app/`: Android Application (Kotlin + CameraX + TFLite).
- `backend/`: ML Pipeline (Scraping, Training, Quantization).
- `docs/`: Technical documentation.

## Tech Stack
- **Mobile:** Kotlin, CameraX API.
- **ML:** MobileNetV2, TensorFlow Lite (INT8 Quantization).
- **Backend Ops:** Python, `uv` for package management.

## Quick Links
- [Getting Started](docs/getting_started.md)
- [Architecture](docs/architecture.md)
- [Model Details](docs/model_detail.md)
