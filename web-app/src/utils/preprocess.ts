import * as tf from '@tensorflow/tfjs';

const IMG_SIZE = 224;

export async function imageFileToTensor(file: File): Promise<tf.Tensor3D> {
  const bitmap = await createImageBitmap(file, {
    resizeWidth: IMG_SIZE,
    resizeHeight: IMG_SIZE,
    resizeQuality: 'high',
  });

  const canvas = new OffscreenCanvas(IMG_SIZE, IMG_SIZE);
  const ctx = canvas.getContext('2d')!;
  ctx.drawImage(bitmap, 0, 0);
  bitmap.close();

  const imageData = ctx.getImageData(0, 0, IMG_SIZE, IMG_SIZE);
  const { data, width, height } = imageData;

  // RGBA → RGB uint8 [0, 255], channel-last
  const rgb = new Uint8Array(width * height * 3);
  for (let i = 0; i < width * height; i++) {
    const srcIdx = i * 4;
    const dstIdx = i * 3;
    rgb[dstIdx] = data[srcIdx];       // R
    rgb[dstIdx + 1] = data[srcIdx + 1]; // G
    rgb[dstIdx + 2] = data[srcIdx + 2]; // B
  }

  return tf.tensor3d(rgb, [height, width, 3], 'float32');
}
