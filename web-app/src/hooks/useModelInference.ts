import { useCallback, useEffect, useRef, useState } from 'react';
import { loadLiteRt, loadAndCompile, Tensor, CompiledModel } from '@litertjs/core';
import { imageFileToTensor } from '../utils/preprocess';
import type { ReportStatus } from '../types/laporan';

const LABELS: ReportStatus[] = ['AMAN', 'WASPADA', 'BAHAYA'];
const MODEL_URL = '/models/retak/retak_mobilenetv2.tflite';
const WASM_PATH = '/wasm/';

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

export function useModelInference(): UseModelInferenceReturn {
  const modelRef = useRef<CompiledModel | null>(null);
  const [isModelReady, setIsModelReady] = useState(false);
  const [isPredicting, setIsPredicting] = useState(false);
  const [modelError, setModelError] = useState<string | null>(null);

  useEffect(() => {
    let disposed = false;

    async function load() {
      try {
        await loadLiteRt(WASM_PATH);
        if (disposed) return;

        const compiled = await loadAndCompile(MODEL_URL);
        if (disposed) { compiled.delete(); return; }

        const warmup = Tensor.fromTypedArray(new Uint8Array(224 * 224 * 3), [1, 224, 224, 3]);
        await compiled.run(warmup);
        warmup.delete();

        modelRef.current = compiled;
        setIsModelReady(true);
        setModelError(null);
      } catch (err) {
        setModelError(err instanceof Error ? err.message : 'Gagal memuat model');
      }
    }

    load();
    return () => {
      disposed = true;
      modelRef.current?.delete();
      modelRef.current = null;
    };
  }, []);

  const predict = useCallback(async (file: File): Promise<PredictionResult> => {
    const model = modelRef.current;
    if (!model) throw new Error('Model belum siap');

    setIsPredicting(true);
    try {
      const { data, shape } = await imageFileToTensor(file);
      const input = Tensor.fromTypedArray(data, shape);

      const outputs = await model.run(input);
      const output = outputs[0];
      const raw = await output.data();

      input.delete();
      output.delete();

      const logits = Array.from(raw);

      const maxLogit = Math.max(...logits);
      const exps = logits.map((v) => Math.exp(v - maxLogit));
      const sumExps = exps.reduce((a, b) => a + b, 0);
      const probs = exps.map((v) => v / sumExps);
      const argmax = probs.indexOf(Math.max(...probs));

      return {
        status: LABELS[argmax],
        confidence: probs[argmax],
        probabilities: probs,
      };
    } finally {
      setIsPredicting(false);
    }
  }, []);

  return { isModelReady, isPredicting, modelError, predict };
}
