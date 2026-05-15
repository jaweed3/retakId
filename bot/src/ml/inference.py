from dataclasses import dataclass, field

import numpy as np

from ml.preprocess import preprocess_image

try:
    import tflite_runtime.interpreter as tflite
except ImportError:
    from tensorflow import lite as tflite

LABELS = ["AMAN", "WASPADA", "BAHAYA"]


@dataclass
class PredictionResult:
    label: str
    confidence: float
    probabilities: list[float] = field(default_factory=list)
    is_certain: bool = True


class ModelPredictor:

    def __init__(self, model_path: str, confidence_threshold: float = 0.5):
        self.interpreter = tflite.Interpreter(
            model_path=model_path,
            num_threads=2,
        )
        self.interpreter.allocate_tensors()

        self.input_details = self.interpreter.get_input_details()
        self.output_details = self.interpreter.get_output_details()

        dtype = self.input_details[0]["dtype"]
        assert dtype == np.uint8, f"Expected uint8 input, got {dtype}"

        self.confidence_threshold = confidence_threshold

    def predict(self, image_path: str) -> PredictionResult:
        input_tensor = preprocess_image(image_path)

        self.interpreter.set_tensor(
            self.input_details[0]["index"], input_tensor,
        )
        self.interpreter.invoke()

        output = self.interpreter.get_tensor(
            self.output_details[0]["index"],
        )[0]

        if output.dtype == np.uint8:
            logits = output.astype(np.float32) / 255.0
        else:
            logits = output.astype(np.float32)

        max_logit = logits.max()
        exp = np.exp(logits - max_logit)
        probs = exp / exp.sum()

        argmax = int(np.argmax(probs))
        confidence = float(probs[argmax])

        is_certain = confidence >= self.confidence_threshold

        if not is_certain:
            label = "TIDAK_PASTI"
        else:
            label = LABELS[argmax]

        return PredictionResult(
            label=label,
            confidence=confidence,
            probabilities=[float(p) for p in probs],
            is_certain=is_certain,
        )
