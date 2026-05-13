const IMG_SIZE = 224;

export interface PreprocessedImage {
  data: Uint8Array;
  shape: number[];
}

export async function imageFileToTensor(file: File): Promise<PreprocessedImage> {
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
  const { data } = imageData;

  const rgb = new Uint8Array(IMG_SIZE * IMG_SIZE * 3);
  for (let i = 0; i < IMG_SIZE * IMG_SIZE; i++) {
    const s = i * 4, d = i * 3;
    rgb[d] = data[s];
    rgb[d + 1] = data[s + 1];
    rgb[d + 2] = data[s + 2];
  }

  return { data: rgb, shape: [1, IMG_SIZE, IMG_SIZE, 3] };
}
