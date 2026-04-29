# Technical Architecture

## Overview
Retak.id uses an "Edge-First" approach. All landslide crack detection is performed on-device to ensure functionality in areas with poor internet connectivity.

## Data Flow
1. **Camera Input**: Android CameraX captures real-time frames.
2. **Pre-processing**: Frames are converted to Bitmaps, resized to 224x224, and normalized.
3. **Inference**: TFLite INT8 model processes the frame locally.
4. **Output**: UI displays classification (AMAN, WASPADA, BAHAYA).

## ML Pipeline (Backend)
- **Scraping**: Targeted images from various sources to supplement small datasets.
- **Augmentation**: Extreme augmentation to combat overfitting.
- **Quantization**: Post-Training Quantization (PTQ) to reduce model size to <5MB.
