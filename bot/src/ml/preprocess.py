import numpy as np
from PIL import Image

INPUT_SIZE = 224


def preprocess_image(image_path: str) -> np.ndarray:
    img = Image.open(image_path).convert("RGB")
    img = img.resize((INPUT_SIZE, INPUT_SIZE), Image.BILINEAR)
    arr = np.array(img, dtype=np.uint8)
    arr = np.expand_dims(arr, axis=0)
    return arr
