import { useCallback, useEffect, useRef, useState } from 'react';
import * as tf from '@tensorflow/tfjs';
import { imageFileToTensor } from '../utils/preprocess';
import type { ReportStatus } from '../types/laporan';

const LABELS: ReportStatus[] = ['AMAN', 'WASPADA', 'BAHAYA'];

interface PredictionResult {
  status: ReportStatus;
  confidence: number;
  probabilities: number[];
}

interface UseModelInferenceReturn {
  isModelReady: boolean;
  isPredicting: boolean;
  modelError: string | null;
  predict: (file: File) => Promise<PredictionResult>;
}

const MODEL_URL = '/models/retak/model.json';

export function useModelInference(): UseModelInferenceReturn {
  const modelRef = useRef<tf.GraphModel | null>(null);
  const [isModelReady, setIsModelReady] = useState(false);
  const [isPredicting, setIsPredicting] = useState(false);
  const [modelError, setModelError] = useState<string | null>(null);

  useEffect(() => {
    let disposed = false;

    async function load() {
      try {
        await tf.ready();
        const model = await tf.loadGraphModel(MODEL_URL);
        if (disposed) {
          model.dispose();
          return;
        }
        modelRef.current = model;

        // Warmup — WebGL shader compilation biar prediksi pertama gak lambat
        const warmupTensor = tf.zeros([1, 224, 224, 3], 'float32');
        model.predict(warmupTensor);
        tf.dispose(warmupTensor);

        setIsModelReady(true);
        setModelError(null);
      } catch (err) {
        setModelError(err instanceof Error ? err.message : 'Gagal memuat model');
      }
    }

    load();

    return () => {
      disposed = true;
      modelRef.current?.dispose();
      modelRef.current = null;
    };
  }, []);

  const predict = useCallback(async (file: File): Promise<PredictionResult> => {
    const model = modelRef.current;
    if (!model) throw new Error('Model belum siap');

    setIsPredicting(true);
    try {
      const tensor3d = await imageFileToTensor(file);
      const batched = tf.expandDims(tensor3d, 0) as tf.Tensor4D;

      const output = model.predict(batched) as tf.Tensor;
      const raw = await output.data();
      const logits = Array.from(raw instanceof Float32Array ? raw : new Float32Array(raw));

      tf.dispose([tensor3d, batched, output]);

      // Softmax
      const maxLogit = Math.max(...logits);
      const exps = logits.map((v) => Math.exp(v - maxLogit));
      const sumExps = exps.reduce((a, b) => a + b, 0);
      const probs = exps.map((v) => v / sumExps);

      const argmax = probs.indexOf(Math.max(...probs));
      const status = LABELS[argmax];
      const confidence = probs[argmax];

      return { status, confidence, probabilities: probs };
    } finally {
      setIsPredicting(false);
    }
  }, []);

  return { isModelReady, isPredicting, modelError, predict };
}
