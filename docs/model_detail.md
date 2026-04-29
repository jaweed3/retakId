# Model Details

## Architecture
- **Base Model**: MobileNetV2 (Pre-trained on ImageNet).
- **Reasoning**: Lightweight depthwise separable convolutions are ideal for mobile devices and small datasets.

## Training Strategy
- **Transfer Learning**: Freeze feature extraction layers, train only the classification head.
- **Optimizer**: Adam (lr=1e-4).
- **Loss**: Categorical Crossentropy.

## Quantization (PTQ)
- **Type**: INT8 Quantization.
- **Input/Output**: Integer mapping.
- **Target Size**: <5MB (Reduced from ~14MB FP32).
- **Benefit**: 4x memory reduction and significant latency improvement on low-end devices.
